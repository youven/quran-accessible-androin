package com.youven.quranaccessible

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val preferences = getSharedPreferences("reading_settings", MODE_PRIVATE)
        setContent {
            var easyMode by rememberSaveable {
                mutableStateOf(preferences.getBoolean("easy_mode", true))
            }
            var preview by rememberSaveable { mutableStateOf(false) }
            BackHandler(enabled = preview) { preview = false }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.fillMaxSize().safeDrawingPadding()
                                .verticalScroll(rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Text(
                                stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.semantics { heading() }
                            )
                            if (preview) {
                                Text(
                                    stringResource(R.string.sample),
                                    fontSize = if (easyMode) 28.sp else 20.sp,
                                    lineHeight = if (easyMode) 44.sp else 32.sp
                                )
                                Button(
                                    onClick = { preview = false },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                ) { Text(stringResource(R.string.back)) }
                            } else {
                                Text(
                                    stringResource(R.string.welcome),
                                    style = MaterialTheme.typography.headlineSmall
                                )
                                Text(
                                    stringResource(R.string.intro),
                                    fontSize = if (easyMode) 24.sp else 18.sp,
                                    lineHeight = if (easyMode) 36.sp else 28.sp
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .toggleable(
                                            value = easyMode,
                                            role = Role.Switch,
                                            onValueChange = {
                                                easyMode = it
                                                preferences.edit().putBoolean("easy_mode", it).apply()
                                            }
                                        ).padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.easy_mode), fontSize = 22.sp)
                                        Text(stringResource(R.string.easy_description))
                                    }
                                    Switch(checked = easyMode, onCheckedChange = null)
                                }
                                Button(
                                    onClick = { preview = true },
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                ) { Text(stringResource(R.string.preview), fontSize = 20.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}
