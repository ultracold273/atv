package com.example.atv.ui.screens.iptv.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.atv.ui.theme.AtvColors

@Composable
fun IptvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = supportingText?.let { { Text(it) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AtvColors.OnSurface,
            unfocusedTextColor = AtvColors.OnSurface,
            focusedContainerColor = AtvColors.Surface,
            unfocusedContainerColor = AtvColors.Surface,
            cursorColor = AtvColors.Primary,
            focusedBorderColor = AtvColors.Primary,
            unfocusedBorderColor = AtvColors.OnSurfaceVariant,
            focusedLabelColor = AtvColors.Primary,
            unfocusedLabelColor = AtvColors.OnSurfaceVariant,
        ),
    )
}
