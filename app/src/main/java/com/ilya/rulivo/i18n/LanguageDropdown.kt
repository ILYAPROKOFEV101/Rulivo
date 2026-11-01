package com.ilya.rulivo.i18n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ilya.rulivo.R

@Composable
internal fun LanguageDropdown() {
    val resources = LocalResources.current
    val languages = remember(resources) { AppLanguages.supported(resources) }
    val selectedLanguage = AppLanguages.selected
    val systemLabel = stringResource(R.string.language_system)
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("language-picker"),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.language_title), modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                // Autonyms remain recognizable even when the current UI language is unfamiliar.
                Text(selectedLanguage?.getDisplayName(selectedLanguage) ?: systemLabel,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 240.dp)) {
            (listOf(null) + languages).forEach { locale ->
                val isSelected = locale?.language == selectedLanguage?.language
                DropdownMenuItem(
                    text = { Text(locale?.getDisplayName(locale) ?: systemLabel) },
                    onClick = {
                        expanded = false
                        AppLanguages.select(locale)
                    },
                    trailingIcon = {
                        if (isSelected) Icon(Icons.Outlined.Check, contentDescription = null)
                    },
                    modifier = Modifier.testTag("language-option-${locale?.language.orEmpty()}")
                        .semantics { selected = isSelected },
                )
            }
        }
    }
}
