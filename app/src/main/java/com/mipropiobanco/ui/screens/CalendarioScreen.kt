package com.mipropiobanco.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mipropiobanco.domain.Calendario
import com.mipropiobanco.domain.EventoCalendario
import com.mipropiobanco.domain.TipoEvento
import com.mipropiobanco.ui.BancoViewModel
import com.mipropiobanco.ui.components.Encabezado
import com.mipropiobanco.util.Formato
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val formatoDiaLargo = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Formato.localeCO)

@Composable
private fun colorEvento(tipo: TipoEvento): Color = when (tipo) {
    TipoEvento.INGRESO -> MaterialTheme.colorScheme.primary
    TipoEvento.GASTO -> MaterialTheme.colorScheme.error
    TipoEvento.PROGRAMADO -> MaterialTheme.colorScheme.tertiary
    TipoEvento.CUOTA_PRESTAMO -> MaterialTheme.colorScheme.secondary
    TipoEvento.PAGO_DEUDA -> Color(0xFFE67E22)
    TipoEvento.TRASLADO -> MaterialTheme.colorScheme.outline
}

@Composable
fun CalendarioScreen(vm: BancoViewModel) {
    val movimientos by vm.movimientos.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    val plantillas by vm.plantillas.collectAsStateWithLifecycle()
    val prestamos by vm.prestamos.collectAsStateWithLifecycle()
    val deudas by vm.deudas.collectAsStateWithLifecycle()

    var mesTexto by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var diaTexto by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val mes = YearMonth.parse(mesTexto)
    val dia = LocalDate.parse(diaTexto)
    val hoy = LocalDate.now()

    val eventos = Calendario.eventos(mes, movimientos, categorias.associateBy { it.id }, plantillas, prestamos, deudas)
    val delDia = eventos[dia].orEmpty()

    Column(Modifier.fillMaxSize()) {
        Encabezado("Calendario")
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                SelectorMes(mes) { nuevo ->
                    mesTexto = nuevo.toString()
                    diaTexto = (if (YearMonth.from(hoy) == nuevo) hoy else nuevo.atDay(1)).toString()
                }
            }
            item { CuadriculaMes(mes, dia, hoy, eventos) { diaTexto = it.toString() } }
            item { Leyenda() }
            item {
                val ingresos = delDia.filter { it.tipo == TipoEvento.INGRESO }.sumOf { it.monto }
                val gastos = delDia.filter { it.tipo == TipoEvento.GASTO }.sumOf { it.monto }
                Column {
                    Text(
                        dia.format(formatoDiaLargo).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (ingresos > 0L || gastos > 0L) {
                        Text(
                            "Entró ${Formato.cop(ingresos)} · salió ${Formato.cop(gastos)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (delDia.isEmpty()) {
                item { Text("Nada registrado ni programado este día.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(delDia) { e -> FilaEvento(e) }
        }
    }
}

@Composable
private fun CuadriculaMes(
    mes: YearMonth,
    seleccionado: LocalDate,
    hoy: LocalDate,
    eventos: Map<LocalDate, List<EventoCalendario>>,
    alElegir: (LocalDate) -> Unit
) {
    val primero = mes.atDay(1)
    val desfase = primero.dayOfWeek.value - 1 // lunes = 0
    val dias = mes.lengthOfMonth()
    val celdas = desfase + dias
    val semanas = (celdas + 6) / 7

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { d ->
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            for (s in 0 until semanas) {
                Row(Modifier.fillMaxWidth()) {
                    for (d in 0 until 7) {
                        val indice = s * 7 + d - desfase + 1
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(0.85f)
                                .padding(2.dp)
                        ) {
                            if (indice in 1..dias) {
                                val fecha = mes.atDay(indice)
                                CeldaDia(
                                    fecha = fecha,
                                    esHoy = fecha == hoy,
                                    seleccionado = fecha == seleccionado,
                                    eventos = eventos[fecha].orEmpty(),
                                    alElegir = { alElegir(fecha) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CeldaDia(
    fecha: LocalDate,
    esHoy: Boolean,
    seleccionado: Boolean,
    eventos: List<EventoCalendario>,
    alElegir: () -> Unit
) {
    val fondo = when {
        seleccionado -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(fondo)
            .then(
                if (esHoy) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clickable(onClick = alElegir)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            fecha.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (esHoy) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(Modifier.size(2.dp))
        val tipos = eventos.map { it.tipo }.distinct().take(4)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            tipos.forEach { t ->
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colorEvento(t))
                )
            }
        }
        val gastos = eventos.filter { it.tipo == TipoEvento.GASTO }.sumOf { it.monto }
        if (gastos > 0L) {
            Text(
                abreviar(gastos),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1
            )
        }
    }
}

/** 1.250.000 → "1,3M"; 45.000 → "45k". */
private fun abreviar(valor: Long): String = when {
    valor >= 1_000_000L -> String.format(Formato.localeCO, "%.1fM", valor / 1_000_000.0)
    valor >= 1_000L -> "${valor / 1_000L}k"
    else -> valor.toString()
}

@Composable
private fun Leyenda() {
    val elementos = listOf(
        TipoEvento.INGRESO to "Ingreso",
        TipoEvento.GASTO to "Gasto",
        TipoEvento.PROGRAMADO to "Fijo programado",
        TipoEvento.CUOTA_PRESTAMO to "Cuota préstamo",
        TipoEvento.PAGO_DEUDA to "Pago deuda"
    )
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            elementos.take(3).forEach { (t, n) -> ItemLeyenda(t, n) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            elementos.drop(3).forEach { (t, n) -> ItemLeyenda(t, n) }
        }
    }
}

@Composable
private fun ItemLeyenda(tipo: TipoEvento, nombre: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(colorEvento(tipo))
        )
        Spacer(Modifier.width(4.dp))
        Text(nombre, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FilaEvento(e: EventoCalendario) {
    val signo = when (e.tipo) {
        TipoEvento.INGRESO -> "+"
        TipoEvento.GASTO, TipoEvento.CUOTA_PRESTAMO, TipoEvento.PAGO_DEUDA -> "−"
        TipoEvento.PROGRAMADO, TipoEvento.TRASLADO -> ""
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colorEvento(e.tipo))
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(e.titulo, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (e.detalle.isNotBlank()) {
                    Text(e.detalle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("$signo${Formato.cop(e.monto)}", fontWeight = FontWeight.Bold, color = colorEvento(e.tipo))
        }
    }
}
