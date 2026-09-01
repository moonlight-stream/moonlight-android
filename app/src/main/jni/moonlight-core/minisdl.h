#pragma once

#include <stdbool.h>
#include <stdint.h>

typedef uint16_t Uint16;
typedef uint32_t Uint32;

bool SDL_IsJoystickXboxOneElite(Uint16 vendor_id, Uint16 product_id);
bool SDL_IsJoystickXboxSeriesX(Uint16 vendor_id, Uint16 product_id);
bool SDL_IsJoystickDualSenseEdge(Uint16 vendor_id, Uint16 product_id);