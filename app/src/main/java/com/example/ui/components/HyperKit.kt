package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.Hairline
import com.example.ui.theme.HairlineStrong
import com.example.ui.theme.Ink
import com.example.ui.theme.InkFaint
import com.example.ui.theme.InkMuted
import com.example.ui.theme.PaperCanvas
import com.example.ui.theme.PaperSunken
import com.example.ui.theme.PaperSurface
import com.example.ui.theme.PaperTint
import com.example.ui.theme.SignalLime
import com.example.ui.theme.SignalLimeSoft
import com.example.ui.theme.VioletNote
import com.example.ui.theme.VioletSoft

/**
 * Shared primitives of the "Paper & Signal" design language.
 * Everything is built from five ingredients: paper surface, hairline rule,
 * ink type, mono micro-labels and the lime signal.
 */

@Composable
fun HyperCard(
    modifier: Modifier = Modifier,
    color: Color = PaperSurface,
    shape: Shape = RoundedCornerShape(24.dp),
    borderColor: Color = Hairline,
    elevation: Int = 6,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }
    Surface(
        modifier = base
            .shadow(
                elevation = elevation.dp,
                shape = shape,
                clip = false,
                ambientColor = Ink.copy(alpha = 0.10f),
                spotColor = Ink.copy(alpha = 0.10f)
            ),
        shape = shape,
        color = color,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    color: Color = Hairline
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color)
    )
}

@Composable
fun VerticalRule(
    height: Int = 34,
    color: Color = Hairline
) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(height.dp)
            .background(color)
    )
}

@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = InkMuted,
    maxLines: Int = 1
) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** Section heading: small mono label + hairline that runs to the edge. */
@Composable
fun SectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    color: Color = InkMuted
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MonoLabel(text = title, color = color)
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(Hairline)
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

@Composable
fun PillTag(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = SignalLimeSoft,
    foreground: Color = Ink,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Int = 7
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/** Solid ink pill — the single primary action of a screen. */
@Composable
fun InkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = Ink,
    contentColor: Color = PaperCanvas,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(CircleShape)
            .background(if (enabled) containerColor else InkFaint)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(9.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Hairline-outlined pill — secondary or cancelling action. */
@Composable
fun OutlinePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Ink,
    borderColor: Color = Hairline,
    containerColor: Color = PaperSurface
) {
    Row(
        modifier = modifier
            .height(54.dp)
            .clip(CircleShape)
            .background(containerColor)
            .border(1.dp, borderColor, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Selectable mono chip used for category filters. */
@Composable
fun MonoChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg by animateColorAsState(
        targetValue = if (selected) Ink else PaperSurface,
        label = "chip_bg"
    )
    val fg by animateColorAsState(
        targetValue = if (selected) PaperCanvas else InkMuted,
        label = "chip_fg"
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) Ink else Hairline, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1
        )
    }
}

/** Square checkbox drawn in the paper language. */
@Composable
fun PaperCheck(
    checked: Boolean,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (checked) accent else PaperSurface)
            .border(
                width = 1.5.dp,
                color = if (checked) accent else HairlineStrong,
                shape = RoundedCornerShape(7.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun BarMeter(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = Ink,
    track: Color = Hairline,
    height: Int = 6
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun HyperSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Cerca per nome o cartella"
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        shape = CircleShape,
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = InkFaint
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = InkMuted,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PaperSurface,
            unfocusedContainerColor = PaperTint,
            focusedBorderColor = Ink,
            unfocusedBorderColor = Hairline,
            cursorColor = Ink,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            focusedLeadingIconColor = Ink,
            unfocusedLeadingIconColor = InkMuted
        )
    )
}

/** Key / value ledger line used in telemetry tables. */
@Composable
fun LedgerRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Ink,
    labelColor: Color = InkMuted,
    emphasized: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = labelColor,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = valueColor,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Numeric index chip used to number groups and stages. */
@Composable
fun IndexBadge(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = PaperSunken,
    foreground: Color = InkMuted
) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = foreground,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Small square icon holder. */
@Composable
fun IconSquare(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    background: Color = PaperTint,
    tint: Color = Ink,
    size: Int = 38
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.47f).dp)
        )
    }
}

/** Rule-accent banner used for the active auto-selection rule. */
@Composable
fun AccentBanner(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    background: Color = VioletSoft,
    accent: Color = VioletNote
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            MonoLabel(text = title, color = accent)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = "CAMBIA",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LimeHighlightTag(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(SignalLime)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Ink,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun CoralTag(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AlertCoral)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}
