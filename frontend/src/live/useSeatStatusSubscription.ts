import { Client } from "@stomp/stompjs";
import { useEffect, useRef, useState } from "react";
import type { SeatStatus } from "../api/events";

export type SeatStatusUpdate = {
  seatId: string;
  status: SeatStatus;
  version: number;
  timestamp: string;
};

export type LiveConnectionState = "offline" | "connecting" | "connected" | "reconnecting";

type SubscriptionOptions = {
  eventId: string | undefined;
  accessToken: string | undefined;
  onSeatStatus: (update: SeatStatusUpdate) => void;
  onConnected: () => void;
};

export function useSeatStatusSubscription({
  eventId,
  accessToken,
  onSeatStatus,
  onConnected
}: SubscriptionOptions): LiveConnectionState {
  const [connectionState, setConnectionState] = useState<LiveConnectionState>("offline");
  const onSeatStatusRef = useRef(onSeatStatus);
  const onConnectedRef = useRef(onConnected);

  useEffect(() => {
    onSeatStatusRef.current = onSeatStatus;
    onConnectedRef.current = onConnected;
  }, [onConnected, onSeatStatus]);

  useEffect(() => {
    if (eventId === undefined || accessToken === undefined) {
      setConnectionState("offline");
      return undefined;
    }

    setConnectionState("connecting");
    let connectedAtLeastOnce = false;
    const client = new Client({
      webSocketFactory: () => new WebSocket(webSocketUrl(eventId)),
      connectHeaders: { Authorization: `Bearer ${accessToken}` },
      reconnectDelay: 3_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      onConnect: () => {
        connectedAtLeastOnce = true;
        setConnectionState("connected");
        client.subscribe(`/topic/events/${eventId}/seats`, (message) => {
          try {
            onSeatStatusRef.current(JSON.parse(message.body) as SeatStatusUpdate);
          } catch {
            // A malformed notification is ignored; REST reconciliation remains authoritative.
          }
        });
        onConnectedRef.current();
      },
      onWebSocketClose: () => {
        setConnectionState(connectedAtLeastOnce ? "reconnecting" : "connecting");
      },
      onStompError: () => {
        setConnectionState("reconnecting");
      }
    });

    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [accessToken, eventId]);

  return connectionState;
}

function webSocketUrl(eventId: string): string {
  const configuredBase = import.meta.env.VITE_WS_BASE_URL;
  if (configuredBase) {
    return `${configuredBase.replace(/\/$/, "")}/ws/events/${eventId}/seats`;
  }

  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws/events/${eventId}/seats`;
}
