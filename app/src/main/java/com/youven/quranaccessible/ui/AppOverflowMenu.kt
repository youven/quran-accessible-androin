package com.youven.quranaccessible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.youven.quranaccessible.data.MadaniPage
import com.youven.quranaccessible.data.QuranMetadata

@Composable
fun AppOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onQuickJump: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onAbout: () -> Unit,
    onOtherApps: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .width(220.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
    ) {
        OverflowMenuItem("انتقال سريع", Icons.Default.DirectionsRun) {
            onDismissRequest()
            onQuickJump()
        }
        OverflowMenuItem("الإعدادات", Icons.Default.Settings) {
            onDismissRequest()
            onSettings()
        }
        OverflowMenuItem("مساعدة", Icons.Default.HelpOutline) {
            onDismissRequest()
            onHelp()
        }
        OverflowMenuItem("عنا", Icons.Default.Info) {
            onDismissRequest()
            onAbout()
        }
        OverflowMenuItem("تطبيقات أخرى", Icons.Default.Apps) {
            onDismissRequest()
            onOtherApps()
        }
    }
}

@Composable
private fun OverflowMenuItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                fontSize = 16.sp,
                color = Color(0xFF1C2520)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF0D8A74),
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun QuickJumpDialog(
    currentPage: Int,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    var input by remember { mutableStateOf(currentPage.toString()) }
    var selectedSurah by remember { mutableStateOf(QuranMetadata.surahForPage(currentPage).number) }
    var isBySurah by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتقال سريع", fontSize = 20.sp, color = Color(0xFF1C2520)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isBySurah,
                        onClick = { isBySurah = false },
                        label = { Text("رقم الصفحة") }
                    )
                    FilterChip(
                        selected = isBySurah,
                        onClick = { isBySurah = true },
                        label = { Text("اسم السورة") }
                    )
                }

                if (!isBySurah) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("أدخل رقم الصفحة (١ - ٦٠٤)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    var expandedSurah by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(
                            onClick = { expandedSurah = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(QuranMetadata.surah(selectedSurah).let { "سورة ${it.nameArabic} (صفحة ${it.startPage})" })
                        }
                        DropdownMenu(
                            expanded = expandedSurah,
                            onDismissRequest = { expandedSurah = false },
                            modifier = Modifier.heightIn(max = 300.dp)
                        ) {
                            QuranMetadata.SURAHS.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${QuranMetadata.toArabicDigits(s.number)}. سورة ${s.nameArabic} (${s.startPage})") },
                                    onClick = {
                                        selectedSurah = s.number
                                        expandedSurah = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isBySurah) {
                        onJumpToPage(QuranMetadata.surah(selectedSurah).startPage)
                    } else {
                        val parsed = MadaniPage.parse(input) ?: currentPage
                        onJumpToPage(parsed)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0D8A74))
            ) {
                Text("انتقال")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("عن تطبيق قرآن", fontSize = 20.sp, color = Color(0xFF1C2520)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تطبيق مصحف رقمي ميسّر للجميع، مبني على مصحف المدينة المنورة برواية حفص عن عاصم (مجمع الملك فهد).")
                Text("• خطوط القرآن الرسمية: Quran Foundation QCF V2.")
                Text("• النصوص وبيانات الأسطر: Quran.com.")
                Text("• يدعم التكبير والتصغير وتصفح السور والأجزاء مع مرجعيات الصفحات وسجل القراءة.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("إغلاق") }
        }
    )
}

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("المساعدة", fontSize = 20.sp, color = Color(0xFF1C2520)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("• للتنقل بين الصفحات: اسحب أفقياً أو اضغط على أزرار التالية والسابقة.")
                Text("• للتكبير والتصغير: استخدم إصبعيك (Pinch-to-zoom) أو انقر نقراً مزدوجاً على الصفحة.")
                Text("• لحفظ علامة مرجعية: اضغط على أيقونة الإشارة المرجعية (🔖) في أعلى صفحة القراءة.")
                Text("• للبحث: اضغط على أيقونة البحث (🔍) في شاشة السور للبحث بالاسم أو رقم الصفحة.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("فهمت") }
        }
    )
}

@Composable
fun SettingsDialog(
    easyMode: Boolean,
    onToggleEasyMode: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الإعدادات", fontSize = 20.sp, color = Color(0xFF1C2520)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "النص الميسّر وقارئ الشاشة",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1C2520)
                        )
                        Text(
                            text = "عرض نصوص الآيات بخط نظامي واضح متوافق مع أدوات إمكانية الوصول والتكبير العالي.",
                            fontSize = 13.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                    Switch(
                        checked = easyMode,
                        onCheckedChange = onToggleEasyMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF0D8A74)
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("تم") }
        }
    )
}

