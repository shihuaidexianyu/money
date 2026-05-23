package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.domain.model.ACCOUNT_COLOR_NAMES
import com.shihuaidexianyu.money.domain.model.ACCOUNT_ICON_NAMES
import com.shihuaidexianyu.money.domain.model.DEFAULT_ACCOUNT_ICON_NAME
import com.shihuaidexianyu.money.domain.model.normalizeAccountColorName
import com.shihuaidexianyu.money.domain.model.normalizeAccountIconName

data class AccountVisualOption(
    val name: String,
    val label: String,
)

val AccountIconOptions = ACCOUNT_ICON_NAMES.map { name ->
    AccountVisualOption(name = name, label = accountIconLabel(name))
}

val AccountColorOptions = ACCOUNT_COLOR_NAMES.map { name ->
    AccountVisualOption(name = name, label = accountColorLabel(name))
}

fun accountIconLabel(name: String): String {
    return when (normalizeAccountIconName(name)) {
        "bank" -> "银行"
        "card" -> "卡片"
        "cash" -> "现金"
        "savings" -> "储蓄"
        "chart" -> "投资"
        else -> "钱包"
    }
}

fun accountColorLabel(name: String): String {
    return when (normalizeAccountColorName(name)) {
        "green" -> "绿色"
        "orange" -> "橙色"
        "purple" -> "紫色"
        "red" -> "红色"
        "teal" -> "青色"
        "gray" -> "灰色"
        else -> "蓝色"
    }
}

@Composable
fun AccountVisualIcon(
    iconName: String,
    colorName: String,
    modifier: Modifier = Modifier,
    containerSize: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    val accent = accountVisualColor(colorName)
    Box(
        modifier = modifier
            .size(containerSize)
            .background(color = accent.copy(alpha = 0.12f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = accountIconVector(iconName),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun AccountColorSwatch(
    colorName: String,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color = accountVisualColor(colorName), shape = CircleShape),
    )
}

@Composable
private fun accountVisualColor(name: String): Color {
    return when (normalizeAccountColorName(name)) {
        "green" -> Color(0xFF2E7D32)
        "orange" -> Color(0xFFE87124)
        "purple" -> Color(0xFF7E57C2)
        "red" -> Color(0xFFC62828)
        "teal" -> Color(0xFF00897B)
        "gray" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> Color(0xFF2563EB)
    }
}

private fun accountIconVector(name: String): ImageVector {
    return when (normalizeAccountIconName(name)) {
        "bank" -> Icons.Rounded.AccountBalance
        "card" -> Icons.Rounded.CreditCard
        "cash" -> Icons.Rounded.Payments
        "savings" -> Icons.Rounded.Savings
        "chart" -> Icons.AutoMirrored.Rounded.TrendingUp
        DEFAULT_ACCOUNT_ICON_NAME -> Icons.Rounded.AccountBalanceWallet
        else -> Icons.Rounded.AccountBalanceWallet
    }
}
