package com.example.tortugram

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text

/**
 * Pantalla "Dentro del verificador de 2F"
 * isaac-maker 2026
 */

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PasswordScreen(
    hint: String?,
    error: String?,
    onSubmit: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Verificación en dos pasos")

        if (!hint.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Pista: $hint")
        }

        Spacer(modifier = Modifier.height(16.dp))

        BasicTextField(
            value = password,
            onValueChange = { password = it },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier
                .focusRequester(focusRequester)
                .background(Color.DarkGray)
                .padding(12.dp)
                .fillMaxWidth(0.5f)
        )

        if (!error.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .background(Color.Blue)
                .clickable { onSubmit(password) }
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text("Ingresar")
        }
    }
}