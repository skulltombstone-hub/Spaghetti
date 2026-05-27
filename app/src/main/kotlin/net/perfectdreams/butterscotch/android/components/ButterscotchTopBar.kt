package net.perfectdreams.butterscotch.android.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButterscotchTopBar(
    title: String,
    nav: NavHostController,
    navigationIcon: @Composable (() -> Unit) = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFFE8A552),
            titleContentColor = Color.Black
        ),
        navigationIcon = navigationIcon,
        actions = actions,
    )
}
