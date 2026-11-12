package com.svyaz.app.call

import org.webrtc.PeerConnection

/** Настройки звонков. */
object CallConfig {

    /**
     * Серверы, которые помогают двум телефонам найти друг друга в интернете.
     *
     * STUN — бесплатная «справочная»: сообщает телефону его адрес в интернете.
     * Этого достаточно для большинства домашних Wi-Fi и части мобильных сетей.
     *
     * TURN — «ретранслятор»: звук идёт через него, когда напрямую соединиться
     * не получается (так бывает в мобильных и рабочих сетях). Бесплатных надёжных
     * TURN-серверов нет: его арендуют или ставят на свой сервер (программа coturn).
     * Когда у вас появится такой сервер — раскомментируйте пример ниже.
     */
    val iceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.sipnet.ru:3478").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
        PeerConnection.IceServer.builder("stun:stun.zadarma.com:3478").createIceServer(),
        // PeerConnection.IceServer.builder("turn:АДРЕС_ВАШЕГО_СЕРВЕРА:3478")
        //     .setUsername("ЛОГИН")
        //     .setPassword("ПАРОЛЬ")
        //     .createIceServer(),
    )

    /** Сколько ждать ответа на вызов. */
    const val RING_TIMEOUT_MS = 45_000L

    /** Вызов старше этого возраста считается «зависшим» и не показывается. */
    const val STALE_CALL_MS = 120_000L

    /** Сколько ждать восстановления связи, прежде чем завершить звонок. */
    const val RECONNECT_TIMEOUT_MS = 15_000L
}
