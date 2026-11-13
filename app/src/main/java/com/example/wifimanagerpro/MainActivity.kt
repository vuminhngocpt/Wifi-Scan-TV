package com.example.wifimanagerpro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                WifiManagerAppScreen()
            }
        }
    }
}

val COMMON_PORTS = listOf(80, 443, 8080, 21, 22, 3306)

@Composable
fun WifiManagerAppScreen() {
    val context = LocalContext.current
    var wifiStatus by remember { mutableStateOf("Nhấn 'Làm mới' để xem thông tin") }
    var advancedInfo by remember { mutableStateOf("") }
    var scanResults by remember { mutableStateOf<List<ScanResult>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Wi-Fi Manager Pro",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "Kết nối hiện tại", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = wifiStatus, style = MaterialTheme.typography.bodyMedium)
                        if (advancedInfo.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = advancedInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    wifiStatus = getCurrentWifiInfo(context)
                                    advancedInfo = getAdvancedNetworkInfo(context)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Làm mới")
                            }

                            OutlinedButton(
                                onClick = {
                                    context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("Cài đặt Wi-Fi")
                            }
                        }
                    }
                }
            }

            item {
                PingTestCard(context = context)
            }

            item {
                LanScannerSection(context = context)
            }

            item {
                Button(
                    onClick = { scanResults = scanWifiNetworks(context) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Quét mạng Wi-Fi xung quanh (${scanResults.size})")
                }
            }

            items(scanResults) { result ->
                WifiItemCard(result = result)
            }
        }
    }
}

@Composable
fun PingTestCard(context: Context) {
    var pingResult by remember { mutableStateOf("Nhấn để đo độ trễ mạng (ms)") }
    var isPinging by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = "Kiểm tra độ trễ mạng (Ping)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = pingResult, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = {
                    isPinging = true
                    coroutineScope.launch {
                        val gateway = getGatewayAddress(context) ?: "8.8.8.8"
                        val time = pingHost(gateway)
                        pingResult = "Gateway ($gateway): $time"
                        isPinging = false
                    }
                },
                enabled = !isPinging,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Text(if (isPinging) "Đang đo..." else "Đo Ping ngay")
            }
        }
    }
}

@Composable
fun LanScannerSection(context: Context) {
    var isScanning by remember { mutableStateOf(false) }
    var activeIps by remember { mutableStateOf<List<String>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = "Thiết bị trong mạng LAN", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    isScanning = true
                    coroutineScope.launch {
                        activeIps = scanLocalNetwork(context)
                        isScanning = false
                    }
                },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isScanning) "Đang quét..." else "Quét thiết bị LAN")
            }

            if (isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }

            if (activeIps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tìm thấy ${activeIps.size} thiết bị:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(4.dp))

                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    activeIps.forEach { ip ->
                        DeviceItemCard(context = context, ip = ip)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceItemCard(context: Context, ip: String) {
    var isScanningPorts by remember { mutableStateOf(false) }
    var openPorts by remember { mutableStateOf<List<Pair<Int, String>>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "• $ip", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = {
                        isScanningPorts = true
                        expanded = true
                        coroutineScope.launch {
                            openPorts = scanPorts(ip)
                            isScanningPorts = false
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(if (isScanningPorts) "Đang quét..." else "Quét Port", style = MaterialTheme.typography.labelSmall)
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    if (isScanningPorts) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else if (openPorts.isNotEmpty()) {
                        openPorts.forEach { (port, service) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "  Port $port ($service)", style = MaterialTheme.typography.bodySmall)
                                if (port == 80 || port == 443 || port == 8080) {
                                    TextButton(
                                        onClick = {
                                            val scheme = if (port == 443) "https" else "http"
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://$ip:$port"))
                                            context.startActivity(intent)
                                        },
                                        contentPadding = PaddingValues(4.dp)
                                    ) {
                                        Text("Mở Web", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    } else {
                        Text(text = "  Không tìm thấy cổng mở hoặc chưa quét.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
fun WifiItemCard(result: ScanResult) {
    val (qualityText, qualityColor) = getSignalQuality(result.level)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (result.SSID.isNullOrEmpty()) "[Mạng ẩn]" else result.SSID,
                    style = MaterialTheme.typography.titleSmall
                )
                Surface(
                    color = qualityColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = qualityText,
                        color = qualityColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "Tín hiệu: ${result.level} dBm | Tần số: ${result.frequency} MHz", style = MaterialTheme.typography.bodySmall)
        }
    }
}

fun getCurrentWifiInfo(context: Context): String {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val wifiInfo = wifiManager.connectionInfo
    val ssid = wifiInfo.ssid?.replace("\"", "") ?: "Không xác định"
    val ipAddress = android.text.format.Formatter.formatIpAddress(wifiInfo.ipAddress)
    return "- SSID: $ssid\n- IP của bạn: $ipAddress\n- Tốc độ: ${wifiInfo.linkSpeed} Mbps"
}

fun getGatewayAddress(context: Context): String? {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork
    val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
    return linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
}

fun getAdvancedNetworkInfo(context: Context): String {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork
    val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
    val gateway = linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: "Không rõ"
    val dns = linkProperties?.dnsServers?.joinToString(", ") { it.hostAddress ?: "" } ?: "Không rõ"
    return "- Gateway: $gateway\n- DNS: $dns"
}

suspend fun pingHost(host: String): String = withContext(Dispatchers.IO) {
    try {
        val startTime = System.currentTimeMillis()
        val address = InetAddress.getByName(host)
        val reachable = address.isReachable(1000)
        val latency = System.currentTimeMillis() - startTime
        if (reachable) "$latency ms" else "Không phản hồi"
    } catch (e: Exception) {
        "Lỗi kết nối"
    }
}

fun getSignalQuality(rssi: Int): Pair<String, Color> {
    return when {
        rssi >= -50 -> Pair("Rất mạnh", Color(0xFF4CAF50))
        rssi >= -65 -> Pair("Tốt", Color(0xFF2196F3))
        rssi >= -80 -> Pair("Trung bình", Color(0xFFFF9800))
        else -> Pair("Yếu", Color(0xFFF44336))
    }
}

fun scanWifiNetworks(context: Context): List<ScanResult> {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    wifiManager.startScan()
    return wifiManager.scanResults ?: emptyList()
}

fun getSubnetPrefix(context: Context): String? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val ipAddress = wifiManager.connectionInfo.ipAddress
    if (ipAddress == 0) return null
    val ipString = android.text.format.Formatter.formatIpAddress(ipAddress)
    val lastDot = ipString.lastIndexOf('.')
    return if (lastDot != -1) ipString.substring(0, lastDot) else null
}

suspend fun scanLocalNetwork(context: Context): List<String> = withContext(Dispatchers.IO) {
    val subnet = getSubnetPrefix(context) ?: return@withContext emptyList()
    val deferreds = (1..254).map { i ->
        async(Dispatchers.IO) {
            try {
                if (InetAddress.getByName("$subnet.$i").isReachable(100)) "$subnet.$i" else null
            } catch (e: Exception) { null }
        }
    }
    deferreds.awaitAll().filterNotNull()
}

suspend fun scanPorts(ip: String): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
    val openPorts = mutableListOf<Pair<Int, String>>()
    val deferreds = COMMON_PORTS.map { port ->
        async(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 150)
                socket.close()
                val service = when (port) {
                    80, 8080 -> "HTTP"
                    443 -> "HTTPS"
                    21 -> "FTP"
                    22 -> "SSH"
                    3306 -> "MySQL"
                    else -> "Khác"
                }
                Pair(port, service)
            } catch (e: Exception) { null }
        }
    }
    openPorts.addAll(deferreds.awaitAll().filterNotNull())
    openPorts
}
