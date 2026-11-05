package com.moneytracker.app.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.data.TransactionEntity
import com.moneytracker.app.ui.components.EmojiIcon
import com.moneytracker.app.ui.components.money
import com.moneytracker.app.ui.theme.Fin
import com.moneytracker.core.Fmt
import com.moneytracker.core.Ledger
import com.moneytracker.core.TxType

data class TxVisual(val emoji: String, val color: Color, val title: String, val subtitle: String, val amountText: String, val amountColor: Color)

@Composable
fun txVisual(s: Snapshot, t: TransactionEntity, perspectiveWalletId: Long? = null): TxVisual {
    val c = Fin.colors
    val cat = t.categoryId?.let { s.categoryMap[it] }
    val root = s.rootCategory(t.categoryId)
    val from = s.walletMap[t.walletId]?.name ?: "?"
    val to = t.toWalletId?.let { s.walletMap[it]?.name }
    val (emoji, color) = when (t.type) {
        TxType.TRANSFER -> "🔁" to c.transfer
        TxType.SAVINGS_ALLOCATION -> "🐷" to c.savings
        TxType.CC_SETTLEMENT -> "💳" to c.transfer
        TxType.DEBT_PAYMENT, TxType.DEBT_COLLECTION, TxType.LOAN_GIVEN, TxType.LOAN_RECEIVED -> "🤝" to c.warning
        TxType.ADJUSTMENT -> "⚖️" to c.muted
        else -> (cat?.icon ?: if (t.type == TxType.INCOME) "💰" else "💸") to Color(root?.color ?: cat?.color ?: 0xFF7C5CFF)
    }
    val title = t.description.ifBlank {
        when (t.type) {
            TxType.EXPENSE, TxType.INCOME -> cat?.name ?: t.type.label
            else -> t.type.label
        }
    }
    val subtitle = when (t.type) {
        TxType.TRANSFER, TxType.CC_SETTLEMENT -> "$from → ${to ?: "?"}"
        TxType.SAVINGS_ALLOCATION -> "$from → ${to ?: t.goalId?.let { s.goalMap[it]?.name } ?: "Tabungan"}"
        TxType.EXPENSE, TxType.INCOME -> "${s.categoryLabel(t.categoryId)} · $from"
        else -> "${t.type.label} · $from"
    }
    val delta: Long = if (perspectiveWalletId != null) Ledger.walletDelta(s.core(t), perspectiveWalletId) else when (t.type) {
        TxType.INCOME, TxType.DEBT_COLLECTION, TxType.LOAN_RECEIVED -> t.amount
        TxType.EXPENSE, TxType.LOAN_GIVEN, TxType.DEBT_PAYMENT -> -(t.amount + t.fee)
        TxType.ADJUSTMENT -> t.amount
        else -> 0 // perpindahan antar-wallet: netral
    }
    val amountColor = when {
        delta > 0 -> c.income
        delta < 0 -> MaterialTheme.colorScheme.onSurface
        else -> c.transfer
    }
    val amountText = if (delta == 0L) money(t.amount) else money(delta, withSign = true)
    return TxVisual(emoji, color, title, subtitle, amountText, amountColor)
}

@Composable
fun TransactionRow(s: Snapshot, t: TransactionEntity, onClick: () -> Unit, perspectiveWalletId: Long? = null, showDate: Boolean = false) {
    val v = txVisual(s, t, perspectiveWalletId)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        EmojiIcon(v.emoji, v.color, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(v.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (showDate) Fmt.dateShort(t.dateTime) + " · " else Fmt.time(t.dateTime) + " · ") + v.subtitle,
                style = MaterialTheme.typography.bodySmall, color = Fin.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(v.amountText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = v.amountColor)
            if (t.fee > 0) Text("biaya ${money(t.fee)}", style = MaterialTheme.typography.labelSmall, color = Fin.colors.muted)
        }
    }
}
