package com.cyberoperative.game.ui.menu

import com.cyberoperative.game.i18n.tr
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
import androidx.compose.foundation.horizontalScroll
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
    // Pairing shows the CHARACTER's name (owner, 2026-10-13): it works on Steam, direct IP and offline alike.
    uid = uid, name = com.cyberoperative.game.meta.Characters.coopName(p, callsign), permanent = p.permanentUpgrades, opLevel = operativeLevel(p.operativeXp),
    body = com.cyberoperative.game.data.Operatives.byId(p.selectedOperative).bodyId, skin = com.cyberoperative.game.data.OperativeSkins.token(com.cyberoperative.game.meta.StoreManager.effectiveSkin(p)), ready = true,
    gear = com.cyberoperative.game.data.Gear.encode(p.equipped.values),
    euros = p.euros,
    pet = com.cyberoperative.game.meta.Incubators.loadout(p)?.encode().orEmpty()
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
    var status by remember { mutableStateOf(if (backend.available) tr("CONNECTING…") else "") }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val copyText = com.cyberoperative.game.platform.rememberCopyText()
    val shareText = com.cyberoperative.game.platform.rememberShareText()

    LaunchedEffect(backend) {
        if (!backend.available) return@LaunchedEffect
        status = backend.start().fold({ "" }, { tr("OFFLINE · {0}", it.message ?: tr("can't reach the server")) })
    }

    ScreenScaffold(tr("CO-OP"), onBack) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!backend.available) {
                TerminalCard(accent = Palette.Orange) {
                    Column {
                        Text(tr("CO-OP UNAVAILABLE"), color = Palette.Orange, style = MaterialTheme.typography.titleMedium)
                        Text(
                            tr("This build isn't connected to the co-op server yet. Solo play is unaffected."),
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
                if (status.isEmpty() && !backend.platformFriends) RegisterCard { name ->
                    scope.launch {
                        backend.register(name).onFailure { message = it.message }.onSuccess { message = tr("Welcome, {0}", it.callsign) }
                    }
                }
                return@Column
            }

            // PC without Steam: host, or join by IP address.
            if (backend.directIp) {
                DirectIpCard(
                    backend, profileNow.callsign,
                    onRename = { n -> scope.launch { backend.register(n) } },
                    onHost = {
                        scope.launch {
                            backend.hostDirect(roomPlayerFor(profileNow.uid, profileNow.callsign, profile))
                                .onSuccess { onRoom(it) }.onFailure { message = it.message }
                        }
                    },
                    onJoin = { addr ->
                        message = tr("Connecting to {0}…", addr)
                        scope.launch {
                            backend.joinDirect(addr, roomPlayerFor(profileNow.uid, profileNow.callsign, profile))
                                .onSuccess { message = null; onRoom(it) }.onFailure { message = it.message }
                        }
                    }
                )
                return@Column
            }

            // Who am I + the code to share (Steam: your Steam name; friends come from Steam).
            if (backend.platformFriends) TerminalCard(accent = Palette.Cyan) {
                Column {
                    Text(tr("SIGNED IN WITH {0}", backend.platformName.uppercase()), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(profileNow.callsign, color = Palette.TextPrimary, style = MaterialTheme.typography.titleLarge)
                    Text(
                        tr("Invite an online friend below, or use the {0} overlay (Shift+Tab). Invites from friends appear here.", backend.platformName),
                        color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall
                    )
                }
            } else TerminalCard(accent = Palette.Cyan) {
                Column {
                    Text(tr("CALLSIGN"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(profileNow.callsign, color = Palette.TextPrimary, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(tr("YOUR FRIEND CODE"), color = Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
                    Text(profileNow.code, color = Palette.Cyan, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CyberButton(tr("COPY"), Modifier.weight(1f)) {
                            copyText(profileNow.code)
                            message = tr("Friend code copied")
                        }
                        CyberButton(tr("SHARE"), Modifier.weight(1f)) {
                            shareText(tr("Share friend code"), tr("Add me in Cyber Operative co-op! Friend code: {0}", profileNow.code))
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
                onAccept = { scope.launch { backend.answerFriendRequest(r, true).onSuccess { message = tr("You're now friends with {0}", r.fromName) }.onFailure { message = it.message } } },
                onDecline = { scope.launch { backend.answerFriendRequest(r, false) } }
            )

            if (!backend.platformFriends) AddFriendCard { code ->
                scope.launch { backend.sendFriendRequest(code).onSuccess { message = it }.onFailure { message = it.message } }
            }

            val friends by remember(backend) { backend.friends() }.collectAsState(emptyList())
            Text(tr("FRIENDS ({0})", friends.size), color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
            if (friends.isEmpty()) {
                Text(tr("Share your code or enter a friend's to start."), color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            for (f in friends) FriendRow(f,
                onInvite = {
                    scope.launch {
                        backend.invite(f, roomPlayerFor(profileNow.uid, profileNow.callsign, profile))
                            .onSuccess { onRoom(it) }
                            .onFailure { message = it.message }
                    }
                },
                onRemove = if (backend.platformFriends) null else ({ scope.launch { backend.removeFriend(f.uid) } })
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Direct IP co-op: your name, HOST (shows your address), or JOIN a typed address. */
@Composable
private fun DirectIpCard(backend: CoopBackend, name: String, onRename: (String) -> Unit, onHost: () -> Unit, onJoin: (String) -> Unit) {
    var callsign by remember(name) { mutableStateOf(name) }
    var address by remember { mutableStateOf("") }
    val addresses = remember(backend) { backend.localAddresses() }
    val copyText = com.cyberoperative.game.platform.rememberCopyText()
    TerminalCard(accent = Palette.Cyan) {
        Column {
            Text(tr("DIRECT IP CO-OP"), color = Palette.Cyan, style = MaterialTheme.typography.titleMedium)
            Text(
                tr("One of you hosts, the other joins with the host's address. Same network: works as is. " +
                    "Over the internet the host forwards TCP port {0} on their router, or you both use a virtual LAN (Tailscale, ZeroTier, Radmin VPN).", backend.directPort),
                color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = callsign, onValueChange = { callsign = it.take(16); onRename(callsign) },
                label = { Text(tr("YOUR NAME")) }, singleLine = true, colors = neonFieldColors(), modifier = Modifier.fillMaxWidth()
            )
        }
    }
    TerminalCard(accent = Palette.Green) {
        Column {
            Text(tr("HOST A GAME"), color = Palette.Green, style = MaterialTheme.typography.titleSmall)
            Text(
                if (addresses.isEmpty()) tr("Tell your friend this PC's IP address.")
                else tr("Your friend joins with: ") + addresses.joinToString("  ·  ") + tr("   (port {0})", backend.directPort),
                color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton(tr("HOST"), Modifier.weight(1f), accent = Palette.Green, primary = true, onClick = onHost)
                if (addresses.isNotEmpty()) CyberButton(tr("COPY IP"), Modifier.weight(1f)) { copyText(addresses.first()) }
            }
        }
    }
    TerminalCard {
        Column {
            Text(tr("JOIN A GAME"), color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = address, onValueChange = { address = it.trim().take(64) },
                    label = { Text(tr("HOST'S IP ADDRESS")) }, singleLine = true, colors = neonFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CyberButton(tr("JOIN"), accent = Palette.Green, enabled = address.isNotBlank()) { onJoin(address) }
            }
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
            Text(tr("REGISTER FOR CO-OP"), color = Palette.Green, style = MaterialTheme.typography.titleMedium)
            Text(
                tr("Pick the callsign friends will see. You'll get a friend code to share."),
                color = Palette.TextSecondary, style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(Callsign.MAX + 4) },
                label = { Text(tr("CALLSIGN")) }, singleLine = true, colors = neonFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            if (name.isNotEmpty() && problem != null) Text(problem, color = Palette.Orange, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            CyberButton(tr("REGISTER"), Modifier.fillMaxWidth(), accent = Palette.Green, enabled = problem == null) { onRegister(name) }
        }
    }
}

@Composable
private fun AddFriendCard(onSend: (String) -> Unit) {
    var code by remember { mutableStateOf("") }
    TerminalCard {
        Column {
            Text(tr("ADD A FRIEND"), color = Palette.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = code, onValueChange = { code = it.uppercase().take(11) },
                    label = { Text(tr("FRIEND CODE")) }, singleLine = true, colors = neonFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                CyberButton(tr("SEND"), enabled = code.count { it.isLetterOrDigit() } == 8) { onSend(code); code = "" }
            }
        }
    }
}

@Composable
private fun InviteCard(inv: Invite, onJoin: () -> Unit, onDecline: () -> Unit) {
    TerminalCard(accent = Palette.Green) {
        Column {
            Text(tr("{0} WANTS TO RUN CO-OP", inv.fromName), color = Palette.Green, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton(tr("JOIN"), Modifier.weight(1f), accent = Palette.Green, primary = true, onClick = onJoin)
                CyberButton(tr("DECLINE"), Modifier.weight(1f), accent = Palette.Red, onClick = onDecline)
            }
        }
    }
}

@Composable
private fun RequestCard(r: FriendRequest, onAccept: () -> Unit, onDecline: () -> Unit) {
    TerminalCard(accent = Palette.Cyan) {
        Column {
            Text(tr("FRIEND REQUEST · {0}", r.fromName), color = Palette.Cyan, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberButton(tr("ACCEPT"), Modifier.weight(1f), accent = Palette.Green, onClick = onAccept)
                CyberButton(tr("DECLINE"), Modifier.weight(1f), accent = Palette.Red, onClick = onDecline)
            }
        }
    }
}

@Composable
private fun FriendRow(f: Friend, onInvite: () -> Unit, onRemove: (() -> Unit)?) {
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
                Text(if (f.online) tr("ONLINE") else tr("OFFLINE"), color = if (f.online) Palette.Green else Palette.TextMuted, style = MaterialTheme.typography.labelSmall)
            }
            if (confirm) {
                CyberButton(tr("REMOVE?"), accent = Palette.Red) { confirm = false; onRemove?.invoke() }
            } else {
                CyberButton(tr("INVITE"), accent = Palette.Green, enabled = f.online, onClick = onInvite)
                if (onRemove == null) return@Row
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
    infernoUnlocked: Int = 0,
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
    ScreenScaffold(tr("CO-OP LOBBY"), onBack = onLeave) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.status == RoomStatus.ENDED) {
                Text(tr("The room was closed."), color = Palette.Orange, style = MaterialTheme.typography.titleSmall)
            }
            PlayerCard(tr("HOST"), state.host, state.hostOnline)
            PlayerCard(tr("GUEST"), state.guest, state.guestOnline, waiting = room.isHost && state.guest == null)
            Text(
                tr("Co-op can't be paused. Each operative picks its own upgrades; stand next to a downed partner for 3s to revive them. " +
                    "Threats are tougher and more numerous with two. Both of you keep full rewards."),
                color = Palette.TextMuted, style = MaterialTheme.typography.bodySmall
            )
            if (room.isHost) {
                Text(tr("DIFFICULTY"), color = Palette.Cyan, style = MaterialTheme.typography.labelLarge)
                // The host's unlocked tiers (INFERNO ones scroll sideways).
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in Difficulty.entries + listOfNotNull(if (difficulty.inferno > 0) difficulty else Difficulty.inferno(infernoUnlocked))) {
                        CyberButton(
                            d.label, Modifier.width(132.dp), accent = if (d == difficulty) Color(d.color) else Palette.Divider,
                            subtitle = if (d == difficulty) tr("● SELECTED") else " "
                        ) { difficulty = d }
                    }
                }
                if (difficulty.inferno > 0) InfernoStepper(difficulty.inferno, infernoUnlocked) { lv -> Difficulty.inferno(lv)?.let { difficulty = it } }
                CyberButton(
                    tr("START OPERATION"), Modifier.fillMaxWidth(), accent = Palette.Green, primary = true,
                    enabled = partnerHere && state.status == RoomStatus.LOBBY,
                    subtitle = if (partnerHere) null else tr("WAITING FOR YOUR PARTNER")
                ) {
                    scope.launch { room.start(System.nanoTime(), difficulty.name, "CAMPAIGN") }
                }
            } else {
                Text(
                    if (partnerHere) tr("WAITING FOR THE HOST TO START…") else tr("HOST DISCONNECTED"),
                    color = if (partnerHere) Palette.Green else Palette.Orange,
                    style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            CyberButton(tr("LEAVE"), Modifier.fillMaxWidth(), accent = Palette.Red, onClick = onLeave)
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
                    p?.name ?: if (waiting) tr("WAITING FOR YOUR PARTNER…") else "—",
                    color = if (p != null) Palette.TextPrimary else Palette.TextMuted, style = MaterialTheme.typography.titleMedium
                )
            }
            if (p != null) Text(tr("OP LVL {0}", p.opLevel), color = Palette.Cyan, style = MaterialTheme.typography.labelMedium)
        }
    }
}
