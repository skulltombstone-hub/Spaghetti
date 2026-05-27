package net.perfectdreams.butterscotch.android

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import net.perfectdreams.butterscotch.android.components.ButterscotchBackButton
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar

@Composable
fun AboutScreen(nav: NavHostController) {
    Scaffold(
        topBar = { ButterscotchTopBar("About", nav, navigationIcon = { ButterscotchBackButton(nav) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            Text("Butterscotch", style = MaterialTheme.typography.headlineMedium)
            Text("Created by MrPowerGamerBR", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
