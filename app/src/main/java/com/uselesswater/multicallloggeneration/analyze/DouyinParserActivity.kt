// DouyinParserActivity.kt
package com.uselesswater.multicallloggeneration.analyze

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.uselesswater.multicallloggeneration.analyze.DouyinParserScreen
import com.uselesswater.multicallloggeneration.ui.theme.CallLogGenerationTheme

class DouyinParserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CallLogGenerationTheme {
                DouyinParserScreen()
            }
        }
    }
}