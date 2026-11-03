
package com.example.smartmediq
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
private enum class MediQPage {
    HOME,
    PATIENT_LOGIN,
    PATIENT_REGISTER,
    HOSPITAL_ADD_PATIENT,
    HOSPITAL_LOGIN,
    PATIENT_DASHBOARD,
    HOSPITAL_DASHBOARD,
    MY_APPOINTMENTS,
    APPOINTMENT_HISTORY,
    PATIENT_PROFILE,
    HELP_SUPPORT,
    HOSPITAL_INFO
}
data class PatientItem(
    val id: String,
    val name: String,
    val mobile: String = "",
    val token: Int,
    val status: String,
    val paymentStatus: String = "Unpaid"
)
class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val auth = FirebaseAuth.getInstance()
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFF087F8C),
                    secondary = androidx.compose.ui.graphics.Color(0xFF14B8A6)
                )
            ) {
                SmartMediQApp(auth)
            }
        }
    }
}

@Composable
fun SmartMediQApp(auth: FirebaseAuth) {
    val db = FirebaseFirestore.getInstance()
    var page by remember { mutableStateOf(MediQPage.HOME) }
    var paymentStatus by remember {
        mutableStateOf("Unpaid")
    }
    var consultationFee by remember {
        mutableIntStateOf(300)
    }
    var paymentMode by remember {
        mutableStateOf("Cash")
    }
    var patientName by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var currentToken by remember { mutableIntStateOf(24) }
    val currentUserId = auth.currentUser?.uid
    var savedPatientName by remember { mutableStateOf("") }
    var savedPatientEmail by remember { mutableStateOf("") }
    var savedPatientMobile by remember { mutableStateOf("") }

    DisposableEffect(currentUserId) {
        if (currentUserId == null) {
            onDispose { }
        } else {
            val listener = db.collection("patients")
                .document(currentUserId)
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {

                        android.util.Log.e(
                            "SmartMediQ",
                            "Patient loading error",
                            error
                        )

                    } else if (snapshot != null && snapshot.exists()) {

                        android.util.Log.d(
                            "SmartMediQ",
                            "Patient data: ${snapshot.data}"
                        )

                        currentToken = snapshot.getLong("token")?.toInt() ?: 0

                        savedPatientName = snapshot.getString("name") ?: ""
                        savedPatientEmail = snapshot.getString("email") ?: ""
                        savedPatientMobile = snapshot.getString("mobile") ?: ""

                        paymentStatus = snapshot.getString("paymentStatus") ?: "Unpaid"
                        paymentMode = snapshot.getString("paymentMode") ?: "Cash"

                    } else {

                        android.util.Log.e(
                            "SmartMediQ",
                            "Patient document not found. UID: $currentUserId"
                        )
                    }
                }

            onDispose {
                listener.remove()
            }
        }
    }
    var nowServing by remember { mutableIntStateOf(20) }
    val patientList = remember { mutableStateListOf<PatientItem>() }
    DisposableEffect(db) {
        val listener = db.collection("queues")
            .document("main")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && snapshot.exists()) {
                    consultationFee =
                        snapshot.getLong("consultationFee")?.toInt() ?: 300
                }
            }

        onDispose {
            listener.remove()
        }
    }
    DisposableEffect(db) {
        val listener = db.collection("patients")
            .addSnapshotListener { result, error ->

                if (error != null) {
                    message = "Patient loading error: ${error.message}"
                    android.util.Log.e(
                        "SmartMediQ",
                        "Patient loading failed",
                        error
                    )
                    return@addSnapshotListener
                }

                if (result != null) {
                    patientList.clear()

                    result.documents.forEach { document ->

                        patientList.add(
                            PatientItem(
                                id = document.id,
                                name = document.getString("name") ?: "Unknown",
                                mobile = document.getString("mobile") ?: "",
                                token = document.getLong("token")?.toInt() ?: 0,
                                status = document.getString("status") ?: "Waiting",
                                paymentStatus = document.getString("paymentStatus") ?: "Unpaid"
                            )
                        )
                    }

                    android.util.Log.d(
                        "SmartMediQ",
                        "Patients loaded: ${patientList.size}"
                    )
                    message = "Patients loaded: ${patientList.size}"
                }
            }

        onDispose {
            listener.remove()
        }
    }

    var doctorStatus by remember { mutableStateOf("Available") }
    var morningOpd by remember { mutableStateOf("10:00 AM - 2:00 PM") }
    var eveningOpd by remember { mutableStateOf("4:00 PM - 8:00 PM") }
    var eveningOpdInput by remember { mutableStateOf("4:00 PM - 8:00 PM") }
    var breakTimeInput by remember {
        mutableStateOf("2:00 PM - 4:00 PM")
    }
    var breakTime by remember { mutableStateOf("2:00 PM - 4:00 PM") }
    DisposableEffect(db) {
        val listener = db.collection("queues")
            .document("main")
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null && snapshot.exists()) {
                    val serving = snapshot.getLong("nowServing")?.toInt()
                    if (serving != null) {
                        nowServing = serving
                    }

                    doctorStatus = snapshot.getString("doctorStatus") ?: "Available"
                    morningOpd = snapshot.getString("morningOpd")
                        ?: "10:00 AM - 2:00 PM"
                    eveningOpd = snapshot.getString("eveningOpd")
                        ?: "4:00 PM - 8:00 PM"
                    breakTime = snapshot.getString("breakTime")
                        ?: "2:00 PM - 4:00 PM"
                }
            }

        onDispose {
            listener.remove()
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.ui.graphics.Color(0xFFF2FAFA)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            when (page) {
                MediQPage.HOME -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFE9FAFC),
                                        Color.White,
                                        Color(0xFFD9F5F8)
                                    )
                                )
                            )

                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Spacer(modifier = Modifier.height(20.dp))

                        // Heart logo
                        Image(
                            painter = painterResource(
                                id = R.drawable.smart_mediq_logo
                            ),
                            contentDescription = "Medical Heart Logo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(230.dp),
                            contentScale = ContentScale.Fit
                        )

                        // Tagline
                        Text(
                            text = "Less waiting. Better patient care.",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF07869D),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 42.dp)
                        )

                        // Patient Login

                        // New Patient Registration
                        Button(
                            onClick = {
                                message = ""
                                page = MediQPage.PATIENT_REGISTER
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(82.dp),
                            shape = RoundedCornerShape(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF16BDB8)
                            )
                        ) {
                            Text(
                                text = "♙+   │   New Patient\n             Registration   ›",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Hospital Staff Login
                        Button(
                            onClick = {
                                message = ""
                                page = MediQPage.HOSPITAL_LOGIN
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(76.dp),
                            shape = RoundedCornerShape(40.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0875C9)
                            )
                        ) {
                            Text(
                                text = "▣   │   Hospital Staff Login   ›",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.height(30.dp))

                        // Bottom decoration
                        Text(
                            text = "━━━━━━━━  ♥  ━━━━━━━━",
                            color = Color(0xFF54C6D9),
                            fontSize = 20.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                }


                MediQPage.PATIENT_LOGIN,
                MediQPage.PATIENT_REGISTER,
                MediQPage.HOSPITAL_LOGIN -> {
                    val hospital = page == MediQPage.HOSPITAL_LOGIN
                    val register = page == MediQPage.PATIENT_REGISTER

                    Text(
                        when {
                            hospital -> "Hospital Staff Login"
                            register -> "Patient Registration"
                            else -> "Patient Login"
                        },
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(18.dp))

                    if (register) {
                        OutlinedTextField(
                            value = patientName,
                            onValueChange = { patientName = it },
                            label = { Text("Full name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email Address") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = mobile,
                        onValueChange = {
                            if (it.length <= 10 && it.all(Char::isDigit)) {
                                mobile = it
                            }
                        },
                        label = {
                            Text(if (hospital) "Staff mobile number" else "Mobile number")
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Phone
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(14.dp))

                    ActionButton(
                        when {
                            hospital -> "Staff Login"
                            register -> "Create Account"
                            else -> "Login"
                        }
                    ) {
                        when {
                            !android.util.Patterns.EMAIL_ADDRESS
                                .matcher(email.trim()).matches() -> {
                                message = "Enter a valid email address."
                            }

                            password.length < 6 -> {
                                message = "Password must contain at least 6 characters."
                            }

                            register && patientName.isBlank() -> {
                                message = "Please enter your full name."
                            }
                            mobile.isBlank() -> {
                                message = "Please enter mobile number."
                            }

                            !mobile.matches(Regex("^[0-9]{10}$")) -> {
                                message = "Enter valid 10 digit mobile number."
                            }


                            hospital -> {
                                auth.signInWithEmailAndPassword(
                                    email.trim(),
                                    password
                                ).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val uid = auth.currentUser?.uid

                                        if (uid != null) {
                                            db.collection("staff")
                                                .document(uid)
                                                .get()
                                                .addOnSuccessListener { document ->
                                                    if (
                                                        document.exists() &&
                                                        document.getString("role") == "staff" &&
                                                        document.getBoolean("active") == true
                                                    ) {
                                                        message = "Hospital staff login successful!"
                                                        page = MediQPage.HOSPITAL_DASHBOARD
                                                    } else {
                                                        auth.signOut()
                                                        message =
                                                            "Access denied. Hospital staff only."
                                                    }
                                                }
                                                .addOnFailureListener { error ->
                                                    message =
                                                        "Staff verification failed: ${error.message}"
                                                    auth.signOut()
                                                }
                                        } else {
                                            message = "User ID not found."
                                        }
                                    } else {
                                        message = task.exception?.localizedMessage
                                            ?: "Hospital login failed."
                                    }
                                }
                            }


                            register -> {
                                auth.createUserWithEmailAndPassword(
                                    email.trim(),
                                    password
                                ).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val uid = auth.currentUser?.uid

                                        if (uid != null) {
                                            val queueRef = db.collection("queues").document("main")
                                            val patientRef = db.collection("patients").document(uid)

                                            val patientData = mapOf(
                                                "name" to patientName.trim(),
                                                "email" to email.trim(),
                                                "mobile" to mobile,
                                                "paymentStatus" to "Unpaid",
                                                "paymentMode" to "Cash"
                                            )

                                            db.runTransaction { transaction ->

                                                val queueSnapshot = transaction.get(queueRef)
                                                val nextToken = queueSnapshot.getLong("nextToken") ?: 1L

                                                val completePatientData = patientData + mapOf(
                                                    "token" to nextToken,
                                                    "status" to "Waiting"
                                                )

                                                transaction.set(patientRef, completePatientData)
                                                transaction.update(queueRef, "nextToken", nextToken + 1)

                                                nextToken

                                            }.addOnSuccessListener { assignedToken ->

                                                currentToken = assignedToken.toInt()

                                                savedPatientName = patientName.trim()
                                                savedPatientEmail = email.trim()
                                                savedPatientMobile = mobile

                                                paymentStatus = "Unpaid"
                                                paymentMode = "Cash"

                                                message = "Registration successful! Your Token is #$assignedToken"

                                                page = MediQPage.PATIENT_DASHBOARD

                                            }.addOnFailureListener { error ->

                                                message = "Registration failed: ${error.message}"

                                            }
                                        } else {
                                            message = "Account created, but user ID is missing."
                                        }
                                    } else {
                                        message = task.exception?.localizedMessage
                                            ?: "Registration failed."
                                    }
                                }
                            }

                            else -> {
                                auth.signInWithEmailAndPassword(
                                    email.trim(),
                                    password
                                ).addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        message = "Login successful!"
                                        page = MediQPage.PATIENT_DASHBOARD
                                    } else {
                                        message = task.exception?.localizedMessage
                                            ?: "Login failed. Check your credentials."
                                    }
                                }
                            }

                        }
                    }
                    if (message.isNotBlank()) {
                        Text(
                            message,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }

                    if (!hospital) {
                        TextButton(onClick = {
                            message = ""
                            page = if (register) {
                                MediQPage.PATIENT_LOGIN
                            } else {
                                MediQPage.PATIENT_REGISTER
                            }
                        }) {
                            Text(
                                if (register) "Already registered? Login"
                                else "New patient? Create account"
                            )
                        }
                    }

                    TextButton(onClick = {
                        message = ""
                        page = MediQPage.HOME
                    }) {
                        Text("Back to Home")
                    }
                }


                MediQPage.PATIENT_DASHBOARD -> {

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFF2FAFA))
                            .padding(16.dp)
                    ) {

                        // Header
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF087F8C)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Smart MediQ",
                                        fontSize = 23.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Less waiting. Better patient care.",
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                }
                                Text("👤", fontSize = 30.sp)
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Hello, $savedPatientName!",
                                fontSize = 23.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF163653)
                            )

                            var menuExpanded by remember { mutableStateOf(false) }


                            Box {
                                TextButton(onClick = { menuExpanded = true }) {
                                    Text(
                                        text = "☰",
                                        fontSize = 28.sp,
                                        color = Color(0xFF087F8C)
                                    )
                                }

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Patient Details") },
                                        onClick = {
                                            menuExpanded = false
                                            page = MediQPage.PATIENT_PROFILE
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Appointment History") },
                                        onClick = {
                                            menuExpanded = false
                                            page = MediQPage.APPOINTMENT_HISTORY
                                        }
                                    )

                                    DropdownMenuItem(
                                        text = { Text("Logout") },
                                        onClick = {
                                            menuExpanded = false
                                            auth.signOut()
                                            page = MediQPage.HOME
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            "Welcome to your patient dashboard",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )

                        Spacer(Modifier.height(16.dp))

                        // Token Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFDDF5F4)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("YOUR TOKEN NUMBER", fontSize = 14.sp)
                                Text(
                                    if (currentToken > 0) "#$currentToken" else "Not Assigned",
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF087F8C)
                                )
                                Text(
                                    if (currentToken > 0) "● Waiting" else "Waiting for token assignment",
                                    color = Color(0xFF159447),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = "Queue Information",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF087F8C)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Queue Information

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                QueueInfoBox(
                                    "Now Serving",
                                    "Token #$nowServing",
                                    Modifier.weight(1f)
                                )

                                QueueInfoBox(
                                    "Patients Ahead",
                                    "${(currentToken - nowServing - 1).coerceAtLeast(0)} patients",
                                    Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                QueueInfoBox(
                                    "Estimated Waiting Time",
                                    "${(currentToken - nowServing).coerceAtLeast(0) * 10} min",
                                    Modifier.weight(1f)
                                )

                                QueueInfoBox(
                                    "Doctor Status",
                                    doctorStatus,
                                    Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                        // Payment and OPD
                        Text(
                            text = "Payment Details",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF087F8C)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(3.dp)
                        )
                        {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {

                                Spacer(Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Status", fontSize = 13.sp, color = Color.Gray)
                                        Text(
                                            paymentStatus,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }


                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Mode", fontSize = 13.sp, color = Color.Gray)
                                        Text(
                                            paymentMode,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Fee", fontSize = 13.sp, color = Color.Gray)
                                        Text(
                                            "₹$consultationFee",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                    }
                }

                MediQPage.HOSPITAL_DASHBOARD -> {


                    // Hospital Dashboard Header
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF087F8C)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("♥", fontSize = 34.sp, color = Color.White)

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Smart MediQ",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "Less waiting. Better patient care.",
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }

                            Text("🏥", fontSize = 30.sp)
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        "Hospital Dashboard",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF163653)
                    )
                    Text(
                        "Staff Queue Management",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 15.sp,
                        color = Color.Gray
                    )

                    Spacer(Modifier.height(18.dp))
                    // Search Patient
                            Text(
                                "Search Patient",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Enter Patient Name or Mobile Number")
                        },
                        singleLine = true,
                        leadingIcon = {
                            Text("🔍")
                        }
                    )

                    Spacer(Modifier.height(12.dp))
                    val currentlyServingPatient = patientList.firstOrNull {
                        it.status.equals("Consulting", ignoreCase = true)
                    }


                    // Now Serving Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFDDF5F4)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("👥", fontSize = 34.sp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Now Serving", fontSize = 17.sp)
                                Text(
                                    currentlyServingPatient?.let {
                                        "Token #${it.token}"
                                    } ?: "No Patient Currently Serving",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF087F8C)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Next Waiting Token Card
                    val nextWaitingToken = patientList
                        .filter {
                            it.status.equals("Waiting", ignoreCase = true) &&
                                    it.token > 0
                        }
                        .minOfOrNull { it.token }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🕒", fontSize = 34.sp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Next Waiting Token", fontSize = 17.sp)
                                Text(
                                    nextWaitingToken?.let { "Token #$it" }
                                        ?: "No patients waiting",
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF087F8C)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Doctor Status Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White
                        ),
                        elevation = CardDefaults.cardElevation(3.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🩺", fontSize = 34.sp)
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text("Doctor Status", fontSize = 17.sp)
                                Text(
                                    "● $doctorStatus",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (doctorStatus == "Available")
                                        Color(0xFF159447)
                                    else
                                        Color(0xFFCF7B20)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))

                    // Add Patient Button

// Consulting Patient - Show at Top
                    val consultingPatient = patientList.firstOrNull {
                        it.status.equals("Consulting", ignoreCase = true)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFDDF5F4)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                "🩺 Consulting Patient",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF087F8C)
                            )

                            Spacer(Modifier.height(12.dp))

                            if (consultingPatient != null) {

                                Text(
                                    consultingPatient.name,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(12.dp))

                                ActionButton("Mark as Paid") {
                                    db.collection("patients")
                                        .document(consultingPatient.id)
                                        .update(
                                            mapOf(
                                                "paymentStatus" to "Paid",
                                                "paymentMode" to paymentMode
                                            )
                                        )
                                        .addOnSuccessListener {
                                            message = "Payment marked as Paid!"
                                        }
                                        .addOnFailureListener {
                                            message = "Payment update failed."
                                        }
                                }

                                ActionButton("Mark as Complete") {
                                    db.collection("patients")
                                        .document(consultingPatient.id)
                                        .update("status", "Completed")
                                        .addOnSuccessListener {
                                            message = "Consultation completed!"
                                        }
                                        .addOnFailureListener {
                                            message = "Could not complete patient."
                                        }
                                }

                            } else {
                                Text(
                                    "No patient is currently consulting.",
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))


// Add Patient Button
                    ActionButton("➕ Add Patient") {
                        message = ""
                        patientName = ""
                        email = ""
                        mobile = ""
                        page = MediQPage.HOSPITAL_ADD_PATIENT
                    }

                    Text("Total Patients: ${patientList.size}")

// Show all patients and filter when searching
                    val matchingPatients = patientList
                        .filter { patient ->
                            searchQuery.isBlank() ||
                                    patient.name.contains(searchQuery.trim(), ignoreCase = true) ||
                                    patient.mobile.contains(searchQuery.trim())
                        }
                        .sortedBy { it.token }

                    if (matchingPatients.isEmpty()) {
                        Text(
                            if (searchQuery.isBlank()) {
                                "No patients registered yet."
                            } else {
                                "No matching patient found."
                            },
                            color = Color.Gray
                        )
                    } else {
                        matchingPatients.forEach { patient ->

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF2FAFA)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        patient.name,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        "Token: ${
                                            if (patient.token <= 0) "Not Assigned"
                                            else "#${patient.token}"
                                        }"
                                    )

                                    Text("Mobile: ${patient.mobile}")
                                    Text("Status: ${patient.status}")
                                    Text("Payment: ${patient.paymentStatus}")

                                    Spacer(Modifier.height(8.dp))

                                    if (patient.paymentStatus != "Paid") {
                                        ActionButton("Mark as Paid") {
                                            db.collection("patients")
                                                .document(patient.id)
                                                .update(
                                                    mapOf(
                                                        "paymentStatus" to "Paid",
                                                        "paymentMode" to paymentMode
                                                    )
                                                )
                                                .addOnSuccessListener {
                                                    message = "Payment marked as Paid!"
                                                }
                                                .addOnFailureListener {
                                                    message = "Payment update failed."
                                                }
                                        }
                                    }

                                    if (patient.status != "Completed") {
                                        ActionButton("Mark as Complete") {
                                            db.collection("patients")
                                                .document(patient.id)
                                                .update("status", "Completed")
                                                .addOnSuccessListener {
                                                    message = "Patient consultation completed!"
                                                }
                                                .addOnFailureListener {
                                                    message = "Could not complete patient."
                                                }
                                        }
                                    }
                                }
                            }
                        }
                    }


                    var showDoctorStatusOptions by remember { mutableStateOf(false) }

                        ActionButton("Doctor Status: $doctorStatus") {
                            showDoctorStatusOptions = !showDoctorStatusOptions
                        }

                        if (showDoctorStatusOptions) {
                            ActionButton("Available") {
                                db.collection("queues")
                                    .document("main")
                                    .update("doctorStatus", "Available")
                                    .addOnSuccessListener {
                                        doctorStatus = "Available"
                                        message = "Doctor is now available."
                                        showDoctorStatusOptions = false
                                    }
                                    .addOnFailureListener {
                                        message = "Could not update doctor status."
                                    }
                            }

                            ActionButton("On Break") {
                                db.collection("queues")
                                    .document("main")
                                    .update("doctorStatus", "On Break")
                                    .addOnSuccessListener {
                                        doctorStatus = "On Break"
                                        message = "Doctor is now on break."
                                        showDoctorStatusOptions = false
                                    }
                                    .addOnFailureListener {
                                        message = "Could not update doctor status."
                                    }
                            }
                        }



                        ActionButton("Call Next Patient") {

                            // Find the smallest Waiting token
                            val nextPatient = patientList
                                .filter {
                                    it.status.equals("Waiting", ignoreCase = true) &&
                                            it.token > 0
                                }
                                .minByOrNull { it.token }

                            if (nextPatient == null) {

                                message = "No patients waiting."

                            } else {

                                val batch = db.batch()

                                // Complete all currently Consulting patients
                                val currentPatients = patientList.filter {
                                    it.status == "Consulting"
                                }

                                currentPatients.forEach { patient ->
                                    batch.update(
                                        db.collection("patients").document(patient.id),
                                        "status",
                                        "Completed"
                                    )
                                }

                                // Make next patient Consulting
                                batch.update(
                                    db.collection("patients").document(nextPatient.id),
                                    "status",
                                    "Consulting"
                                )

                                // Update Now Serving
                                batch.update(
                                    db.collection("queues").document("main"),
                                    "nowServing",
                                    nextPatient.token
                                )

                                batch.commit()
                                    .addOnSuccessListener {
                                        nowServing = nextPatient.token
                                        message = "Now Serving Token #${nextPatient.token}"
                                    }
                                    .addOnFailureListener { error ->
                                        message = "Update failed: ${error.message}"
                                    }
                            }
                        }





                        if (message.isNotBlank()) {
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Text(
                            "Demo dashboard only; staff access control and shared storage are not connected.",
                            fontSize = 12.sp
                        )

                        ActionButton("Logout") {
                            message = ""
                            page = MediQPage.HOME
                        }
                    }

                    MediQPage.HOSPITAL_ADD_PATIENT -> {
                        Text(
                            "Add New Patient",
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = patientName,
                            onValueChange = { patientName = it },
                            label = { Text("Patient Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = mobile,
                            onValueChange = {
                                if (it.length <= 10 && it.all { c -> c.isDigit() }) mobile = it
                            },
                            label = { Text("Mobile Number") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        ActionButton("Back to Hospital Dashboard") {
                            page = MediQPage.HOSPITAL_DASHBOARD


                        }
                        ActionButton("Save Patient") {
                            val name = patientName.trim()
                            val phone = mobile.trim()
                            val patientEmail = email.trim()

                            if (name.isBlank()) {
                                message = "Please enter patient name."
                            } else if (phone.isBlank()) {
                                message = "Mobile number is required."
                            } else if (phone.length != 10 || !phone.all { it.isDigit() }) {
                                message = "Enter a valid 10-digit mobile number."
                            } else {
                                val patientsRef = db.collection("patients")
                                val queueRef = db.collection("queues").document("main")

                                db.runTransaction { transaction ->
                                    val queueSnapshot = transaction.get(queueRef)

                                    val nextToken = (
                                            queueSnapshot.getLong("nextToken") ?: 1L
                                            ).toInt()

                                    val newPatientRef = patientsRef.document()

                                    val patientData = mapOf(
                                        "name" to name,
                                        "mobile" to phone,
                                        "email" to patientEmail,
                                        "token" to nextToken,
                                        "status" to "Waiting",
                                        "paymentStatus" to "Unpaid",
                                        "paymentMode" to "Cash"
                                    )

                                    transaction.set(newPatientRef, patientData)
                                    transaction.update(queueRef, "nextToken", nextToken + 1L)

                                    nextToken
                                }.addOnSuccessListener { assignedToken ->
                                    message = "Patient added successfully! Token #$assignedToken"
                                    patientName = ""
                                    mobile = ""
                                    email = ""
                                    page = MediQPage.HOSPITAL_DASHBOARD
                                }.addOnFailureListener { error ->
                                    message = "Patient save failed: ${error.message}"
                                }
                            }
                        }
                        if (message.isNotBlank()) {
                            Text(
                                text = message,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }

                    }
                    MediQPage.MY_APPOINTMENTS,
                    MediQPage.APPOINTMENT_HISTORY,
                    MediQPage.PATIENT_PROFILE,
                    MediQPage.HELP_SUPPORT,
                    MediQPage.HOSPITAL_INFO -> {

                        val screenTitle = when (page) {
                            MediQPage.MY_APPOINTMENTS -> "My Appointments"
                            MediQPage.APPOINTMENT_HISTORY -> "Appointment History"
                            MediQPage.PATIENT_PROFILE -> "Patient Profile"
                            MediQPage.HELP_SUPPORT -> "Help & Support"
                            MediQPage.HOSPITAL_INFO -> "Hospital Information"
                            else -> ""
                        }

                        Text(
                            text = screenTitle,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(20.dp))

                        if (page == MediQPage.PATIENT_PROFILE) {

                            Text(
                                text = "Patient Name: $savedPatientName",
                                fontSize = 18.sp
                            )

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = "Email: $savedPatientEmail",
                                fontSize = 18.sp
                            )

                            Spacer(Modifier.height(12.dp))

                            Text(
                                text = "Mobile Number: $savedPatientMobile",
                                fontSize = 18.sp
                            )

                        } else {
                            Text("This section will be available soon.")
                        }
                        ActionButton("Back to Dashboard") {
                            page = MediQPage.PATIENT_DASHBOARD
                        }
                    }

                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }

    @Composable
    private fun ActionButton(
        label: String,
        onClick: () -> Unit
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .heightIn(min = 50.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(label, fontSize = 15.sp)
        }
    }

    @Composable
    private fun InfoCard(
        title: String,
        detail: String
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = androidx.compose.ui.graphics.Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(5.dp))
                Text(detail, fontSize = 14.sp)
            }
        }
    }

    @Composable
    private fun QueueInfoBox(
        title: String,
        detail: String,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier.height(115.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    detail,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF087F8C),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    title,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    color = Color.DarkGray
                )
            }
        }
    }
