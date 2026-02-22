package br.com.boddenb.comidinhas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import br.com.boddenb.comidinhas.data.remote.OpenAiClient
import br.com.boddenb.comidinhas.navigation.AppNav
import br.com.boddenb.comidinhas.ui.screen.theme.ComidinhasTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var openAiClient: OpenAiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ComidinhasTheme {
                AppNav(openAiClient = openAiClient)
            }
        }
    }
}
