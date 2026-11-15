package com.example.shared

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.monetization.PendingOrderStore
import com.example.monetization.ProductsState
import com.example.monetization.MonetizationServices
import com.example.monetization.StoreController
import com.example.shared.i18n.Text
import kotlinx.coroutines.launch

private fun prefsStore(context: Context) = object : PendingOrderStore {
    private val prefs = context.applicationContext.getSharedPreferences("monetization_prefs", Context.MODE_PRIVATE)
    override fun get(): String? = prefs.getString("pending_order_id", null)
    override fun set(orderId: String?) {
        prefs.edit().apply { if (orderId == null) remove("pending_order_id") else putString("pending_order_id", orderId) }.apply()
    }
}

/** Магазин и активация лицензии. Открывается только из родительских настроек (за когнитивным замком). */
@Composable
fun ParentStoreDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val ink = Color(0xFF2B2B2B)
    val controller = remember { StoreController(MonetizationServices.gateway(context), prefsStore(context)) }
    val state by controller.state.collectAsStateWithLifecycle()
    val unlocked by remember { MonetizationServices.unlockedProducts(context) }.collectAsStateWithLifecycle(initialValue = emptySet())
    val scope = rememberCoroutineScope()
    var keyInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { controller.load() }
    LaunchedEffect(state.readyKey) { state.readyKey?.let { keyInput = it } }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFFFFFDE7),
            border = BorderStroke(3.dp, ink),
            modifier = Modifier.testTag("parent_store_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Убрать рекламу", fontSize = 20.sp, fontWeight = FontWeight.Black, color = ink)
                Text("Все игры бесплатны и работают без интернета. Покупка убирает рекламу навсегда; интернет нужен только для покупки и активации.", fontSize = 13.sp, color = ink.copy(alpha = 0.75f))

                when (val p = state.products) {
                    ProductsState.Loading -> Text("Загрузка…", fontSize = 14.sp, color = ink)
                    is ProductsState.Failed -> {
                        Text(p.message, fontSize = 14.sp, color = Color(0xFFB71C1C))
                        TextButton(onClick = { scope.launch { controller.load() } }) { Text("Повторить", color = ink) }
                    }
                    is ProductsState.Loaded -> p.products.forEach { product ->
                        val owned = product.id in unlocked
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(product.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = ink)
                                Text(if (owned) "Куплено ✓" else product.priceLabel, fontSize = 14.sp, color = ink.copy(alpha = 0.8f))
                            }
                            if (!owned) {
                                Button(
                                    enabled = !state.busy,
                                    onClick = { scope.launch { controller.buy(product.id) { url -> openUrl(context, url) } } },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEB3B), contentColor = ink),
                                    modifier = Modifier.testTag("store_buy_${product.id}")
                                ) { Text("Купить", fontWeight = FontWeight.Black) }
                            }
                        }
                    }
                }

                if (state.pendingOrderId != null) {
                    Button(
                        enabled = !state.busy,
                        onClick = { scope.launch { controller.checkPending() } },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3E5FC), contentColor = ink),
                        modifier = Modifier.fillMaxWidth().testTag("store_check_pending")
                    ) { Text("Я оплатил(а) — проверить", fontWeight = FontWeight.Bold) }
                }

                Text("Уже есть ключ?", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = ink)
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it.take(40) },
                    singleLine = true,
                    placeholder = { Text("DPLA-XXXX-XXXX-XXXX") },
                    modifier = Modifier.fillMaxWidth().testTag("store_key_input")
                )
                Button(
                    enabled = !state.busy && keyInput.isNotBlank(),
                    onClick = { scope.launch { controller.activate(keyInput) } },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8E6C9), contentColor = ink),
                    modifier = Modifier.fillMaxWidth().testTag("store_activate")
                ) { Text("Активировать", fontWeight = FontWeight.Black) }

                state.message?.let {
                    Text(it, fontSize = 14.sp, color = if (state.activatedProductId != null) Color(0xFF1B5E20) else ink,
                        modifier = Modifier.testTag("store_message"))
                }
                if (state.busy) Text("Подождите…", fontSize = 13.sp, color = ink.copy(alpha = 0.7f))

                Spacer(Modifier.height(2.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEB3B), contentColor = ink),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Закрыть", fontWeight = FontWeight.Black) }
            }
        }
    }
}

/** Открывает платёжную страницу во внешнем браузере; true, если нашлось приложение для ссылки. */
private fun openUrl(context: Context, url: String): Boolean =
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: android.content.ActivityNotFoundException) {
        false
    }
