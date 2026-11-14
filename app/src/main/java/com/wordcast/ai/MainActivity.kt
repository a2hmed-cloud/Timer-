package com.wordcast.ai

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WordCastApp() }
    }
}

@Composable
private fun WordCastApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var extractedText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("صوّر ورقة أو اختر صورة من جهازك") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            status = "جاري استخراج النص..."
            try {
                val image = InputImage.fromFilePath(context, uri)
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener { result ->
                        extractedText = result.text
                        status = if (result.text.isBlank()) "لم يتم العثور على نص واضح" else "تم استخراج النص — راجعه قبل إنشاء الدرس"
                        busy = false
                    }
                    .addOnFailureListener { error ->
                        status = "تعذر قراءة الصورة: ${error.localizedMessage ?: "خطأ غير معروف"}"
                        busy = false
                    }
            } catch (e: Exception) {
                status = "تعذر فتح الصورة"
                busy = false
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF9BE7D7),
            secondary = Color(0xFFB8C7FF),
            background = Color(0xFF0A1018),
            surface = Color(0xFF121D29)
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0A1018)) {
            Column(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF142638), Color(0xFF0A1018))))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("WORDCAST", color = Color(0xFF9BE7D7), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text("حوّل ورقتك إلى درس صوتي", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.Headphones, null, tint = Color(0xFF9BE7D7), modifier = Modifier.size(38.dp))
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF172838)),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("ابدأ من ورقتك", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("استخرج الكلمات والمصطلحات من صورة، ثم راجعها وجهّز درس النطق والترجمة.", color = Color(0xFFB9C7D5), lineHeight = 22.sp)
                        Button(
                            onClick = { picker.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9BE7D7), contentColor = Color(0xFF10212A))
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null)
                            Spacer(Modifier.width(8.dp))
                            Text("اختيار صورة الورقة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MenuBook, null, tint = Color(0xFF9BE7D7))
                    Spacer(Modifier.width(8.dp))
                    Text("النص المستخرج", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Card(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xCC14212D)),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(16.dp)) {
                        Text(status, color = Color(0xFF9BE7D7), fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        if (busy) {
                            CircularProgressIndicator(color = Color(0xFF9BE7D7))
                        } else if (extractedText.isBlank()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("هنا ستظهر الكلمات التي تم التعرف عليها", color = Color(0xFF718496))
                            }
                        } else {
                            TextField(
                                value = extractedText,
                                onValueChange = { extractedText = it },
                                modifier = Modifier.fillMaxSize(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }
                }
                Text("المرحلة الأولى: استيراد الصورة واستخراج النص", color = Color(0xFF718496), fontSize = 12.sp)
            }
        }
    }
}
