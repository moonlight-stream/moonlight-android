/*
  Simple DirectMedia Layer
  Copyright (C) 1997-2023 Sam Lantinga <slouken@libsdl.org>

  This software is provided 'as-is', without any express or implied
  warranty.  In no event will the authors be held liable for any damages
  arising from the use of this software.

  Permission is granted to anyone to use this software for any purpose,
  including commercial applications, and to alter it and redistribute it
  freely, subject to the following restrictions:

  1. The origin of this software must not be misrepresented; you must not
     claim that you wrote the original software. If you use this software
     in a product, an acknowledgment in the product documentation would be
     appreciated but is not required.
  2. Altered source versions must be plainly marked as such, and must not be
     misrepresented as being the original software.
  3. This notice may not be removed or altered from any source distribution.
*/

// This file includes a few functions standalone from SDL_joystick.c useful for joystick typing

#include "minisdl.h"
#include "usb_ids.h"

SDL_bool SDL_IsJoystickXboxOneElite(Uint16 vendor_id, Uint16 product_id)
{
    if (vendor_id == USB_VENDOR_MICROSOFT) {
        if (product_id == USB_PRODUCT_XBOX_ONE_ELITE_SERIES_1 ||
            product_id == USB_PRODUCT_XBOX_ONE_ELITE_SERIES_2 ||
            product_id == USB_PRODUCT_XBOX_ONE_ELITE_SERIES_2_BLUETOOTH ||
            product_id == USB_PRODUCT_XBOX_ONE_ELITE_SERIES_2_BLE) {
            return SDL_TRUE;
        }
    }
    return SDL_FALSE;
}

SDL_bool SDL_IsJoystickXboxSeriesX(Uint16 vendor_id, Uint16 product_id)
{
    if (vendor_id == USB_VENDOR_MICROSOFT) {
        if (product_id == USB_PRODUCT_XBOX_SERIES_X ||
            product_id == USB_PRODUCT_XBOX_SERIES_X_BLE) {
            return SDL_TRUE;
        }
    }
    if (vendor_id == USB_VENDOR_PDP) {
        if (product_id == USB_PRODUCT_XBOX_SERIES_X_VICTRIX_GAMBIT ||
            product_id == USB_PRODUCT_XBOX_SERIES_X_PDP_BLUE ||
            product_id == USB_PRODUCT_XBOX_SERIES_X_PDP_AFTERGLOW) {
            return SDL_TRUE;
        }
    }
    if (vendor_id == USB_VENDOR_POWERA_ALT) {
        if ((product_id >= 0x2001 && product_id <= 0x201a) ||
            product_id == USB_PRODUCT_XBOX_SERIES_X_POWERA_FUSION_PRO2 ||
            product_id == USB_PRODUCT_XBOX_SERIES_X_POWERA_MOGA_XP_ULTRA ||
            product_id == USB_PRODUCT_XBOX_SERIES_X_POWERA_SPECTRA) {
            return SDL_TRUE;
        }
    }
    if (vendor_id == USB_VENDOR_HORI) {
        if (product_id == USB_PRODUCT_HORI_FIGHTING_COMMANDER_OCTA_SERIES_X ||
            product_id == USB_PRODUCT_HORI_HORIPAD_PRO_SERIES_X) {
            return SDL_TRUE;
        }
    }
    if (vendor_id == USB_VENDOR_RAZER) {
        if (product_id == USB_PRODUCT_RAZER_WOLVERINE_V2 ||
            product_id == USB_PRODUCT_RAZER_WOLVERINE_V2_CHROMA) {
            return SDL_TRUE;
        }
    }
    if (vendor_id == USB_VENDOR_THRUSTMASTER) {
        if (product_id == USB_PRODUCT_THRUSTMASTER_ESWAPX_PRO) {
            return SDL_TRUE;
        }
    }
    if (vendor_id == USB_VENDOR_TURTLE_BEACH) {
        if (product_id == USB_PRODUCT_TURTLE_BEACH_SERIES_X_REACT_R ||
            product_id == USB_PRODUCT_TURTLE_BEACH_SERIES_X_RECON) {
            return SDL_TRUE;
        }
    }
    if (vendor_id == USB_VENDOR_8BITDO) {
        if (product_id == USB_PRODUCT_8BITDO_XBOX_CONTROLLER) {
            return SDL_TRUE;
        }
    }
    if (vendor_id == USB_VENDOR_GAMESIR) {
        if (product_id == USB_PRODUCT_GAMESIR_G7) {
            return SDL_TRUE;
        }
    }
    return SDL_FALSE;
}

SDL_bool SDL_IsJoystickDualSenseEdge(Uint16 vendor_id, Uint16 product_id)
{
    if (vendor_id == USB_VENDOR_SONY) {
        if (product_id == USB_PRODUCT_SONY_DS5_EDGE) {
            return SDL_TRUE;
        }
    }
    return SDL_FALSE;
}