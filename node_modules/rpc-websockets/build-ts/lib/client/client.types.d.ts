import NodeWebSocket from "ws";
export declare type BrowserWebSocketType = InstanceType<typeof WebSocket>;
export declare type NodeWebSocketType = InstanceType<typeof NodeWebSocket>;
export declare type NodeWebSocketTypeOptions = NodeWebSocket.ClientOptions;
export interface IWSClientAdditionalOptions {
    autoconnect?: boolean;
    reconnect?: boolean;
    reconnect_interval?: number;
    max_reconnects?: number;
}
export interface ICommonWebSocketFactory {
    (address: string, options: IWSClientAdditionalOptions): ICommonWebSocket;
}
export interface ICommonWebSocket {
    send: (data: Parameters<BrowserWebSocketType["send"]>[0], optionsOrCallback: ((error?: Error) => void) | Parameters<NodeWebSocketType["send"]>[1], callback?: (error?: Error) => void) => void;
    close: (code?: number, reason?: string) => void;
    addEventListener<K extends keyof WebSocketEventMap>(type: K, listener: (ev: WebSocketEventMap[K]) => any, options?: boolean | AddEventListenerOptions): void;
}
