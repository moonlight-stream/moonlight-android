#pragma once

typedef int SDL_bool;
#define SDL_TRUE 1
#define SDL_FALSE 0

typedef unsigned short Uint16;

SDL_bool SDL_IsJoystickXboxOneElite(Uint16 vendor_id, Uint16 product_id);
SDL_bool SDL_IsJoystickXboxSeriesX(Uint16 vendor_id, Uint16 product_id);
SDL_bool SDL_IsJoystickDualSenseEdge(Uint16 vendor_id, Uint16 product_id);