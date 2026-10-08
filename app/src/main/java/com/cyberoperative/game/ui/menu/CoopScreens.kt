package com.cyberoperative.game.ui.menu

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyberoperative.game.engine.Difficulty
import com.cyberoperative.game.net.Callsign
import com.cyberoperative.game.net.CoopBackend
import com.cyberoperative.game.net.CoopRoom
import com.cyberoperative.game.net.Friend
import com.cyberoperative.game.net.FriendRequest
import com.cyberoperative.game.net.Invite
import com.cyberoperative.game.net.RoomPlayer
import com.cyberoperative.game.net.RoomState
import com.cyberoperative.game.net.RoomStatus
import com.cyberoperative.game.save.PlayerProfile
import com.cyberoperative.game.ui.common.CyberButton
import com.cyberoperative.game.ui.common.ScreenScaffold
import com.cyberoperative.game.ui.common.TerminalCard
import com.cyberoperative.game.ui.theme.Palette
import kotlinx.coroutines.launch

/** What this player brings into a co-op room. */
fun roomPlayerFor(uid: String, callsign: String, p: PlayerProfile) = RoomPlayer(
    uid = uid, name = callsign, permanent = p.permanentUpgrades, opLevel = operativeLevel(p.operativeXp),
    body = p.operativeBody, skin = p.selectedSkin, ready = true
)

/**
 * CO-OP hub (owner, 2026-10-08): register a callsign, share your friend code,
 * add friends, and invite an online friend into a two-player run.
 */
@Composable
fun CoopScreen(
    backend: CoopBackend,
    profile: PlayerProfile,
    onBack: () -> Unit,
    onRoom: (CoopRoom) -> Unit
) {
    val me by backend.profile.collectAsState()
    var status by remember { mutableStateOf(if (backend.available) "CONNECTING…" else "") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    LaunchedEffect(backend) {
        if (!backend.available) return@LaunchedEffect
        status = backend.start().fold({ "" }, { "OFFLINE · ${it.message ?: "can't reach the server"}" })
    }

    ScreenScaffold("CO-OP", onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!backend.available) {
                TerminalCard(accent = Palette.Orange) {
                    Column {
                        Text("CO-OP UNAVAILABLE", color = Palette.Orange, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "This build isn't connected to the co-op server yet. Solo play is unaffected.",
                            color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                return@Column
            }
            if (status.isNotEmpty()) Text(status, color = Palette.TextMuted, style = MaterialTheme.typography.labelMedium)
            message?.let { Text(it, color = Palette.Green, style = MaterialTheme.typography.labelMedium) }

            val profileNow = me
            if (profileNow == null) {
                if (status.isEmpty()) RegisterCard { name ->
                    scope.launch {
                        backend.register(name).onFailure { message = it.message }.onSuccess { message = "Welcome, ${it.callsign}" }
                    }
                }
                return@Column
            }

            // Who am I + the code to share.
            TerminalCard(accent = Palette.Cyan) {
                Column {
                    Text("CALLSIGN", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(profileNow.callsign, color = Palette.TextPrimary, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text("YOUR FRIEND CODE", color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(profileNow.code, color = Palette.Cyan, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CyberButton("COPY", Modifier.weight(1f)) {
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("Friend code", profileNow.code))
                            message = "Friend code copied"
                        }
                        CyberButton("SHARE", Modifier.weight(1f)) {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Add me in Cyber Operative co-op! Friend code: ${profileNow.code}")
                            }
                            ctx.startActivity(Intent.createChooser(send, "Share friend code"))
                        }
                    }
                }
            }

            val invites by remember(backend) { backend.invites() }.collectAsState(emptyList())
            for (inv in invites) InviteCard(inv,
                onJoin = {
                    scope.launch {
                        backend.answerInvite(inv, true, roomPlayerFor(profileNow.uid, profileNow.callsign, profile))
                            .onSuccess { room -> if (room != null) onRoom(room) }
                            .onFailure { message = it.message }
                    }
                },
                onDecline = { scope.launch { backend.answerInvite(inv, false, roomPlayerFor(profileNow.uid, profileNow.callsign, profile)) } }
            )

            val requests by remember(backend) { backend.friendRequests() }.collectAsState(emptyList())
            for (r in requests) RequestCard(r,
                onAccept = { scope.launch { backend.answerFriendRequest(r, true).onSuccess { message = "You're now friends with ${r.fromName}" }.onFailure { message = it.message } } },
                onDecline = { scope.launch { backend.answerFriendRequest(r, false) } }
            )

            AddFriendCard { code ->
                scope.launch { backend.sendFriendRequest(code).onSuccess { message = it }.onFailure { message = it.message } }
            }

            val friends by remember(backend) { backend.friends() }.collectAsState(emptyList())
            Text("FRIENDS (${friends.size})", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
            if (friends.isEmpty()) {
                Text("Share your code or enter a friend's to start.", color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            for (f in friends) FriendRow(f,
                onInvite = {
                    scope.launch {
                        backend.invite(f, roomPlayerFor(profileNow.uid, profileNow.callsign, profile))
                            .onSuccess { onRoom(it) }
                            .onFailure { message = it.message }
                    }
                },
                onRemove = { scope.launch { backend.removeFriend(f.uid) } }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun neonFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Cyan, unfocusedBorderColor = Palette.Divider,
    focusedTextColor = Palette.TextPrimary, unfocusedTextColor = Palette.TextPrimary,
    cursorColor = Palette.Cyan, focusedLabelColor = Palette.Cyan, unfocusedLabelColor = Palette.TextMuted
)

@Composable
private fun RegisterCard(onRegister: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val problem = Callsign.problem(name)
    TerminalCard(accent = Palette.Green) {
        Column {
            Text("REGISTER FOR CO-OP", color = Palette.Green, style = MaterialTheme.typography.titleMedium)
            Text(
                "Pick the callsign friends will see. You'll get a friend code to share.",
                color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(Callsign.MAX + 4) },
                label = { Text("CALLSIGN") }, singleLine = true, colors = neonFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            if (name.isNotEmpty() && problem != null) Text(problem, color = Palette.Orange, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            CyberButton("REGISTER", Modifier.fillMaxWidth(), accent = Palette.Green, enabled = problem == null) { onRegister(name) }
        }
    }
}

@Composable
private fun AddFriendCard(onSend: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    TerminalCard {
        Column {
            Text("ADD A FRIEND", color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase().take(11) },
                    label = { Text("FRIEND CODE") }, singleLine = true, colors = neonFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CyberButton("SEND", enabled = code.count { it.isLetterOrDigit() } == 8) { onSend(code); code = "" }
            }
        }
    }
}

@Composable
private fun InviteCard(inv: Invite, onJoin: () -> Unit, onDecline: () -> Unit) {
    TerminalCard(accent = Palette.Green) {
        Column {
            Text("${inv.fromName} WANTS TO RUN CO-OP", color = Palette.Green, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton("JOIN", Modifier.weight(1f), accent = Palette.Green, primary = true, onClick = onJoin)
                CyberButton("DECLINE", Modifier.weight(1f), accent = Palette.Red, onClick = onDecline)
            }
        }
    }
}

@Composable
private fun RequestCard(r: FriendRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    TerminalCard(accent = Palette.Cyan) {
        Column {
            Text("FRIEND REQUEST · ${r.fromName}", color = Palette.Cyan, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton("ACCEPT", Modifier.weight(1f), accent = Palette.Green, onClick = onAccept)
                CyberButton("DECLINE", Modifier.weight(1f), accent = Palette.Red, onClick = onDecline)
            }
        }
    }
}

@Composable
private fun FriendRow(f: Friend, onInvite: () -> Unit, onRemove: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    TerminalCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (f.online) Palette.Green else Palette.TextMuted.copy(alpha = 0.5f), CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(f.callsign, color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
                Text(if (f.online) "ONLINE" else "OFFLINE", color = if (f.online) Palette.Green else Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            if (confirm) {
                CyberButton("REMOVE?", accent = Palette.Red) { confirm = false; onRemove() }
            } else {
                CyberButton("INVITE", accent = Palette.Green, enabled = f.online, onClick = onInvite)
                Spacer(Modifier.width(6.dp))
                Text(
                    "✕", color = Palette.TextMuted, style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .padding(4.dp)
                        .border(1.dp, Palette.Divider, RoundedCornerShape(4.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .clickable { confirm = true }
                )
            }
        }
    }
}

/**
 * The two-player lobby. The host picks the difficulty and starts; both
 * screens jump into the run when the room flips to PLAYING.
 */
@Composable
fun CoopLobbyScreen(
    room: CoopRoom,
    initialDifficulty: Difficulty,
    onStart: (RoomState) -> Unit,
    onLeave: () -> Unit
) {
    val state by room.state.collectAsState(RoomState(room.roomId))
    var difficulty by remember { mutableStateOf(initialDifficulty) }
    val scope = rememberCoroutineScope()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(state.status) {
        if (state.status == RoomStatus.PLAYING && !started) {
            started = true
            onStart(state)
        }
    }
    val partnerHere = if (room.isHost) state.guest != null && state.guestOnline else state.host != null && state.hostOnline
    ScreenScaffold("CO-OP LOBBY", onBack = onLeave) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.status == RoomStatus.ENDED) {
                Text("The room was closed.", color = Palette.Orange, style = MaterialTheme.typography.titleSmall)
            }
            PlayerCard("HOST", state.host, state.hostOnline)
            PlayerCard("GUEST", state.guest, state.guestOnline, waiting = room.isHost && state.guest == null)
            Text(
                "Co-op can't be paused. Each operative picks its own upgrades; stand next to a downed partner for 3s to revive them. " +
                    "Threats are tougher and more numerous with two. Both of you keep full rewards.",
                color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall
            )
            if (room.isHost) {
                Text("DIFFICULTY", color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in Difficulty.entries) {
                        CyberButton(
                            d.name, Modifier.weight(1f), accent = if (d == difficulty) Color(d.color) else Palette.Divider,
                            subtitle = if (d == difficulty) "● SELECTED" else " "
                        ) { difficulty = d }
                    }
                }
                CyberButton(
                    "START OPERATION", Modifier.fillMaxWidth(), accent = Palette.Green, primary = true,
                    enabled = partnerHere && state.status == RoomStatus.LOBBY,
                    subtitle = if (partnerHere) null else "WAITING FOR YOUR PARTNER"
                ) {
                    scope.launch { room.start(System.nanoTime(), difficulty.name, "CAMPAIGN") }
                }
            } else {
                Text(
                    if (partnerHere) "WAITING FOR THE HOST TO START…" else "HOST DISCONNECTED",
                    color = if (partnerHere) Palette.Green else Palette.Orange,
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            CyberButton("LEAVE", Modifier.fillMaxWidth(), accent = Palette.Red, onClick = onLeave)
        }
    }
}

@Composable
private fun PlayerCard(role: String, p: RoomPlayer?, online: Boolean, waiting: Boolean = false) {
    TerminalCard(accent = if (p != null && online) Palette.Green else Palette.Divider) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (p != null && online) Palette.Green else Palette.TextMuted.copy(alpha = 0.5f), CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(role, color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                Text(
                    p?.name ?: if (waiting) "INVITE SENT · WAITING…" else "—",
                    color = if (p != null) Palette.TextPrimary else Palette.TextMuted, style = MaterialTheme.typography.titleMedium
                )
            }
            if (p != null) Text("OP LVL ${p.opLevel}", color = Palette.Cyan, style = MaterialTheme.typography.labelMedium)
        }
    }
}
