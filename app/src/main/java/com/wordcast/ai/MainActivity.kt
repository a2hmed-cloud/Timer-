package com.wordcast.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WordCastApp() }
    }
}

private fun smartExtract(text: String): List<String> {
    val phraseBank = listOf("extreme weather", "heat wave", "ice sheet", "large-scale", "flood-prone", "marginalized communities", "global warming", "climate change", "greenhouse gases", "sea level", "renewable energy")
    val normalized = text.lowercase(Locale.US).replace(Regex("[^a-z\\s-]"), " ")
    val phrases = phraseBank.filter { normalized.contains(it) }
    val ignored = setOf("n", "v", "adj", "adv", "ed", "ted", "ing", "d")
    val words = Regex("[A-Za-z][A-Za-z'-]{1,}").findAll(text)
        .map { it.value.trim() }.filter { it.lowercase(Locale.US) !in ignored }
        .distinctBy { it.lowercase(Locale.US) }.toList()
    val phraseWords = phrases.flatMap { it.split(" ") }.toSet()
    return (phrases + words.filterNot { it.lowercase(Locale.US) in phraseWords }).distinctBy { it.lowercase(Locale.US) }
}

@Composable
private fun WordCastApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var rawText by remember { mutableStateOf("") }
    var wordText by remember { mutableStateOf("") }
    var translated by remember { mutableStateOf(false) }
    var translations by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var status by remember { mutableStateOf("اختار صورة لورقة الكلمات أو الصق الكلمات بنفسك") }
    var busy by remember { mutableStateOf(false) }
    var voice by remember { mutableStateOf("ذكر") }
    var speaking by remember { mutableStateOf(false) }
    var activeTerm by remember { mutableStateOf("") }
    var activeLetter by remember { mutableStateOf(-1) }
    var activeStage by remember { mutableStateOf("") }
    var motionEnabled by remember { mutableStateOf(true) }
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                tts.value?.language = Locale.US
                tts.value?.setSpeechRate(1f)
            }
        }
        tts.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            tts.value = null
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            status = "جاري قراءة الصورة..."
            try {
                val image = InputImage.fromFilePath(context, uri)
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener { result ->
                        rawText = result.text
                        val tokens = smartExtract(result.text)
                        wordText = tokens.joinToString("\n")
                        translations = emptyMap()
                        translated = false
                        status = "اتعرفنا على ${tokens.size} كلمة. راجع القائمة وعدّلها."
                        busy = false
                    }
                    .addOnFailureListener { e ->
                        status = "فشل استخراج النص: ${e.localizedMessage ?: "حاول بصورة أوضح"}"
                        busy = false
                    }
            } catch (e: Exception) {
                status = "تعذر فتح الصورة"
                busy = false
            }
        }
    }

    val saveText = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val lines = wordText.lines().filter { it.isNotBlank() }
            val content = lines.joinToString("\n") { w -> "$w — ${translations[w] ?: "لم تتم الترجمة"}" }
            runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(content) } }
            status = "تم حفظ قائمة الكلمات والترجمة"
        }
    }

    fun translateWords() {
        val words = wordText.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(Locale.US) }
        if (words.isEmpty()) { status = "أضف كلمات الأول"; return }
        busy = true
        status = "جاري تجهيز الترجمة الإنجليزية ← العربية..."
        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage("en").setTargetLanguage("ar").build()
        )
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                val resultMap = mutableMapOf<String, String>()
                fun next(index: Int) {
                    if (index >= words.size) {
                        translations = resultMap.toMap()
                        translated = true
                        busy = false
                        status = "تمت ترجمة ${resultMap.size} كلمة. الترجمة متاحة بعد تنزيل النموذج."
                        translator.close()
                        return
                    }
                    val w = words[index]
                    translator.translate(w)
                        .addOnSuccessListener { ar -> resultMap[w] = ar; next(index + 1) }
                        .addOnFailureListener { resultMap[w] = "تعذرت الترجمة"; next(index + 1) }
                }
                next(0)
            }
            .addOnFailureListener { e ->
                busy = false
                status = "تعذر تنزيل نموذج الترجمة. اتصل بالإنترنت وحاول مجدداً."
                translator.close()
            }
    }

    fun playLesson() {
        val engine = tts.value
        val words = wordText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (engine == null || words.isEmpty()) { status = "أضف كلمات قبل تشغيل الدرس"; return }
        engine.stop()
        engine.language = Locale.US
        engine.setPitch(if (voice == "ذكر") 0.88f else 1.12f)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                val value = id.orEmpty()
                android.os.Handler(context.mainLooper).post {
                    val match = Regex("spell(\\d+)_(\\d+)").find(value)
                    if (match != null) {
                        val wi = match.groupValues[1].toInt()
                        activeTerm = words.getOrNull(wi).orEmpty()
                        activeLetter = match.groupValues[2].toInt()
                        activeStage = "تهجئة حرفًا حرفًا"
                    } else {
                        activeLetter = -1
                        val wi = Regex("(?:slow|normal|fast|arabic)(\\d+)").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: -1
                        activeTerm = words.getOrNull(wi).orEmpty()
                        activeStage = when {
                            value.startsWith("slow") -> "بطيء جدًا"
                            value.startsWith("normal") -> "عادي"
                            value.startsWith("fast") -> "سريع"
                            value.startsWith("arabic") -> "المعنى بالعربي"
                            else -> "الدرس"
                        }
                    }
                }
            }
            override fun onDone(id: String?) {}
            @Deprecated("Deprecated in Java") override fun onError(id: String?) {}
        })
        words.forEachIndexed { index, w ->
            val ar = translations[w] ?: "الترجمة غير متاحة"
            engine.setSpeechRate(0.55f)
            engine.speak(w, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "slow$index")
            engine.setSpeechRate(1.0f)
            engine.speak(w, TextToSpeech.QUEUE_ADD, null, "normal$index")
            engine.setSpeechRate(1.3f)
            engine.speak(w, TextToSpeech.QUEUE_ADD, null, "fast$index")
            w.forEachIndexed { letterIndex, ch ->
                if (ch.isLetter()) engine.speak(ch.toString(), TextToSpeech.QUEUE_ADD, null, "spell" + index + "_" + letterIndex)
            }
            engine.setSpeechRate(1.0f)
            engine.speak(ar, TextToSpeech.QUEUE_ADD, null, "arabic$index")
        }
        speaking = true
        status = "الدرس شغال: بطيء جدًا، عادي، سريع، تهجئة حرفًا حرفًا، ثم المعنى العربي."
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF9BE7D7), secondary = Color(0xFFB8C7FF), background = Color(0xFF0A1018), surface = Color(0xFF172838))) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF0A1018)) {
            Column(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF142638), Color(0xFF0A1018)))).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("WORDCAST AI", color = Color(0xFF9BE7D7), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text("حوّل الكلمات إلى درس صوتي", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Default.Headphones, null, tint = Color(0xFF9BE7D7), modifier = Modifier.size(34.dp))
                }
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF172838)), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Button(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("اختيار صورة الورقة")
                        }
                        Text("أو اكتب الكلمات بنفسك، كل كلمة في سطر:", color = Color(0xFFB9C7D5), fontSize = 13.sp)
                        OutlinedTextField(
                            value = wordText, onValueChange = { wordText = it; translated = false },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 180.dp),
                            placeholder = { Text("apple\nimportant\nbeautiful") },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF9BE7D7), unfocusedBorderColor = Color(0xFF456070))
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = voice == "ذكر", onClick = { voice = "ذكر" }, label = { Text("صوت ذكر") })
                            FilterChip(selected = voice == "أنثى", onClick = { voice = "أنثى" }, label = { Text("صوت أنثى") })
                        }
                        Button(onClick = { translateWords() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Translate, null); Spacer(Modifier.width(7.dp)); Text("ترجمة الكلمات للعربية")
                        }
                        Button(onClick = { playLesson() }, enabled = !busy && wordText.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Headphones, null); Spacer(Modifier.width(7.dp)); Text("تشغيل الدرس الصوتي")
                        }
                        OutlinedButton(onClick = { saveText.launch("WordCast-word-list.txt") }, modifier = Modifier.fillMaxWidth()) {
                            Text("حفظ قائمة الكلمات والترجمة")
                        }
                    }
                }
                Text(status, color = Color(0xFF9BE7D7), fontSize = 12.sp)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF9BE7D7))
                Text("معاينة الدرس المتحرك", color = Color.White, fontWeight = FontWeight.Bold)
                if (activeTerm.isNotBlank()) {
                    val pulse by animateFloatAsState(if (motionEnabled) 1.035f else 1f, animationSpec = tween(350), label = "wordPulse")
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B3448)), shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().scale(pulse)) {
                        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(activeStage, color = Color(0xFF9BE7D7), fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.Center) {
                                activeTerm.forEachIndexed { i, ch ->
                                    Text(ch.toString(), color = if (i == activeLetter) Color(0xFF0A1018) else Color.White,
                                        modifier = Modifier.padding(horizontal = 2.dp).background(if (i == activeLetter) Color(0xFF9BE7D7) else Color.Transparent, RoundedCornerShape(6.dp)).padding(horizontal = 3.dp, vertical = 5.dp),
                                        fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(translations[activeTerm] ?: "", color = Color(0xFF9BE7D7))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("الكلمات والتعبيرات", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("الحركة", color = Color(0xFFB9C7D5), fontSize = 12.sp)
                    Switch(checked = motionEnabled, onCheckedChange = { motionEnabled = it })
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    itemsIndexed(wordText.lines().filter { it.isNotBlank() }) { _, w ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xCC172838)), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(w, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(if (w.contains(" ")) "تعبير متعدد الكلمات" else "كلمة مفردة", color = Color(0xFF8EA6B8), fontSize = 10.sp)
                                }
                                Text(translations[w] ?: "—", color = Color(0xFF9BE7D7))
                            }
                        }
                    }
                }
                Text("النطق يعتمد على محرك تحويل النص إلى كلام المثبّت على جهازك.", color = Color(0xFF718496), fontSize = 10.sp)
            }
        }
    }
}
