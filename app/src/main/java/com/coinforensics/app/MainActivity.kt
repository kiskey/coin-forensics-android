package com.coinforensics.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.coinforensics.app.ui.AppViewModel
import com.coinforensics.app.ui.CoinForensicsApp
import com.coinforensics.app.ui.CoinForensicsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoinForensicsTheme {
                val vm: AppViewModel = viewModel()
                CoinForensicsApp(vm)
            }
        }
    }
}
