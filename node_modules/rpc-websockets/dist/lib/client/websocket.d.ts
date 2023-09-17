import WebSocket from "ws";
import { IWSClientAdditionalOptions } from "./client.types";
/**
 * factory method for common WebSocket instance
 * @method
 * @param {String} address - url to a websocket server
 * @param {(Object)} options - websocket options
 * @return {Undefined}
 */
export default function (address: string, options: IWSClientAdditionalOptions & WebSocket.ClientOptions): WebSocket;
