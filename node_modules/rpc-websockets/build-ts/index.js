"use strict";
var __rest = (this && this.__rest) || function (s, e) {
    var t = {};
    for (var p in s) if (Object.prototype.hasOwnProperty.call(s, p) && e.indexOf(p) < 0)
        t[p] = s[p];
    if (s != null && typeof Object.getOwnPropertySymbols === "function")
        for (var i = 0, p = Object.getOwnPropertySymbols(s); i < p.length; i++) {
            if (e.indexOf(p[i]) < 0 && Object.prototype.propertyIsEnumerable.call(s, p[i]))
                t[p[i]] = s[p[i]];
        }
    return t;
};
import WebSocket from "./lib/client/websocket";
import CommonClient from "./lib/client";
export class Client extends CommonClient {
    constructor(address = "ws://localhost:8080", _a = {}, generate_request_id) {
        var { autoconnect = true, reconnect = true, reconnect_interval = 1000, max_reconnects = 5 } = _a, rest_options = __rest(_a, ["autoconnect", "reconnect", "reconnect_interval", "max_reconnects"]);
        super(WebSocket, address, Object.assign({ autoconnect,
            reconnect,
            reconnect_interval,
            max_reconnects }, rest_options), generate_request_id);
    }
}
export { default as Server } from "./lib/server";
