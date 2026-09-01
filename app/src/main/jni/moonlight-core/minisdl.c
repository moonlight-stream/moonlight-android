/*
  Simple DirectMedia Layer
  Copyright (C) 1997-2026 Sam Lantinga <slouken@libsdl.org>

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
#include "controller_type.h"
#include "controller_list.h"
#include "usb_ids.h"

// Macro to combine a USB vendor ID and product ID into a single Uint32 value
#define MAKE_VIDPID(VID, PID) (((Uint32)(VID)) << 16 | (PID))

static Uint32 initial_old_xboxone_controllers[] = {
        MAKE_VIDPID(0x0000, 0x6686),
        MAKE_VIDPID(0x0079, 0x18a1),
        MAKE_VIDPID(0x0079, 0x18c2),
        MAKE_VIDPID(0x0079, 0x18c8),
        MAKE_VIDPID(0x0079, 0x18cf),
        MAKE_VIDPID(0x03f0, 0x0495),
        MAKE_VIDPID(0x045e, 0x02d1),
        MAKE_VIDPID(0x045e, 0x02dd),
        MAKE_VIDPID(0x045e, 0x02e0),
        MAKE_VIDPID(0x045e, 0x02e3),
        MAKE_VIDPID(0x045e, 0x02ea),
        MAKE_VIDPID(0x045e, 0x02fd),
        MAKE_VIDPID(0x045e, 0x02ff),
        MAKE_VIDPID(0x045e, 0x0867),
        MAKE_VIDPID(0x045e, 0x0b00),
        MAKE_VIDPID(0x045e, 0x0b05),
        MAKE_VIDPID(0x045e, 0x0b0a),
        MAKE_VIDPID(0x045e, 0x0b0c),
        MAKE_VIDPID(0x045e, 0x0b20),
        MAKE_VIDPID(0x045e, 0x0b21),
        MAKE_VIDPID(0x045e, 0x0b22),
        MAKE_VIDPID(0x046d, 0x0000),
        MAKE_VIDPID(0x046d, 0x1004),
        MAKE_VIDPID(0x046d, 0x1007),
        MAKE_VIDPID(0x046d, 0x1008),
        MAKE_VIDPID(0x046d, 0xf301),
        MAKE_VIDPID(0x0738, 0x02a0),
        MAKE_VIDPID(0x0738, 0x4a01),
        MAKE_VIDPID(0x0738, 0x7263),
        MAKE_VIDPID(0x0738, 0xb738),
        MAKE_VIDPID(0x0738, 0xcb29),
        MAKE_VIDPID(0x0738, 0xf401),
        MAKE_VIDPID(0x0c12, 0x0e17),
        MAKE_VIDPID(0x0c12, 0x0e1c),
        MAKE_VIDPID(0x0c12, 0x0e22),
        MAKE_VIDPID(0x0c12, 0x0e30),
        MAKE_VIDPID(0x0d62, 0x9a1a),
        MAKE_VIDPID(0x0d62, 0x9a1b),
        MAKE_VIDPID(0x0e00, 0x0e00),
        MAKE_VIDPID(0x0e6f, 0x012a),
        MAKE_VIDPID(0x0e6f, 0x0139),
        MAKE_VIDPID(0x0e6f, 0x013B),
        MAKE_VIDPID(0x0e6f, 0x013a),
        MAKE_VIDPID(0x0e6f, 0x0145),
        MAKE_VIDPID(0x0e6f, 0x0146),
        MAKE_VIDPID(0x0e6f, 0x0152),
        MAKE_VIDPID(0x0e6f, 0x015b),
        MAKE_VIDPID(0x0e6f, 0x015c),
        MAKE_VIDPID(0x0e6f, 0x015d),
        MAKE_VIDPID(0x0e6f, 0x015f),
        MAKE_VIDPID(0x0e6f, 0x0160),
        MAKE_VIDPID(0x0e6f, 0x0161),
        MAKE_VIDPID(0x0e6f, 0x0162),
        MAKE_VIDPID(0x0e6f, 0x0163),
        MAKE_VIDPID(0x0e6f, 0x0164),
        MAKE_VIDPID(0x0e6f, 0x0165),
        MAKE_VIDPID(0x0e6f, 0x0166),
        MAKE_VIDPID(0x0e6f, 0x0167),
        MAKE_VIDPID(0x0e6f, 0x0205),
        MAKE_VIDPID(0x0e6f, 0x0206),
        MAKE_VIDPID(0x0e6f, 0x0246),
        MAKE_VIDPID(0x0e6f, 0x0261),
        MAKE_VIDPID(0x0e6f, 0x0262),
        MAKE_VIDPID(0x0e6f, 0x02a0),
        MAKE_VIDPID(0x0e6f, 0x02a1),
        MAKE_VIDPID(0x0e6f, 0x02a2),
        MAKE_VIDPID(0x0e6f, 0x02a3),
        MAKE_VIDPID(0x0e6f, 0x02a4),
        MAKE_VIDPID(0x0e6f, 0x02a5),
        MAKE_VIDPID(0x0e6f, 0x02a6),
        MAKE_VIDPID(0x0e6f, 0x02a7),
        MAKE_VIDPID(0x0e6f, 0x02a8),
        MAKE_VIDPID(0x0e6f, 0x02a9),
        MAKE_VIDPID(0x0e6f, 0x02aa),
        MAKE_VIDPID(0x0e6f, 0x02ab),
        MAKE_VIDPID(0x0e6f, 0x02ac),
        MAKE_VIDPID(0x0e6f, 0x02ad),
        MAKE_VIDPID(0x0e6f, 0x02ae),
        MAKE_VIDPID(0x0e6f, 0x02af),
        MAKE_VIDPID(0x0e6f, 0x02b0),
        MAKE_VIDPID(0x0e6f, 0x02b1),
        MAKE_VIDPID(0x0e6f, 0x02b2),
        MAKE_VIDPID(0x0e6f, 0x02b3),
        MAKE_VIDPID(0x0e6f, 0x02b5),
        MAKE_VIDPID(0x0e6f, 0x02b6),
        MAKE_VIDPID(0x0e6f, 0x02b8),
        MAKE_VIDPID(0x0e6f, 0x02bd),
        MAKE_VIDPID(0x0e6f, 0x02be),
        MAKE_VIDPID(0x0e6f, 0x02bf),
        MAKE_VIDPID(0x0e6f, 0x02c0),
        MAKE_VIDPID(0x0e6f, 0x02c1),
        MAKE_VIDPID(0x0e6f, 0x02c2),
        MAKE_VIDPID(0x0e6f, 0x02c3),
        MAKE_VIDPID(0x0e6f, 0x02c4),
        MAKE_VIDPID(0x0e6f, 0x02c5),
        MAKE_VIDPID(0x0e6f, 0x02c6),
        MAKE_VIDPID(0x0e6f, 0x02c7),
        MAKE_VIDPID(0x0e6f, 0x02c8),
        MAKE_VIDPID(0x0e6f, 0x02c9),
        MAKE_VIDPID(0x0e6f, 0x02ca),
        MAKE_VIDPID(0x0e6f, 0x02cb),
        MAKE_VIDPID(0x0e6f, 0x02cd),
        MAKE_VIDPID(0x0e6f, 0x02ce),
        MAKE_VIDPID(0x0e6f, 0x02cf),
        MAKE_VIDPID(0x0e6f, 0x02d5),
        MAKE_VIDPID(0x0e6f, 0x0346),
        MAKE_VIDPID(0x0e6f, 0x0446),
        MAKE_VIDPID(0x0e6f, 0xf501),
        MAKE_VIDPID(0x0f0d, 0x0063),
        MAKE_VIDPID(0x0f0d, 0x0067),
        MAKE_VIDPID(0x0f0d, 0x0078),
        MAKE_VIDPID(0x0f0d, 0x0097),
        MAKE_VIDPID(0x0f0d, 0x00ba),
        MAKE_VIDPID(0x0f0d, 0x00c0),
        MAKE_VIDPID(0x0f0d, 0x00c5),
        MAKE_VIDPID(0x0f0d, 0x00d8),
        MAKE_VIDPID(0x0f0d, 0x00ed),
        MAKE_VIDPID(0x0fff, 0x02a1),
        MAKE_VIDPID(0x12ab, 0x0304),
        MAKE_VIDPID(0x1430, 0x0291),
        MAKE_VIDPID(0x1430, 0x02a9),
        MAKE_VIDPID(0x1430, 0x070b),
        MAKE_VIDPID(0x1430, 0x0719),
        MAKE_VIDPID(0x146b, 0x0611),
        MAKE_VIDPID(0x1532, 0x0a00),
        MAKE_VIDPID(0x1532, 0x0a03),
        MAKE_VIDPID(0x1532, 0x0a14),
        MAKE_VIDPID(0x1532, 0x0a15),
        MAKE_VIDPID(0x16d0, 0x0f3f),
        MAKE_VIDPID(0x1bad, 0x028e),
        MAKE_VIDPID(0x1bad, 0x02a0),
        MAKE_VIDPID(0x1bad, 0x5500),
        MAKE_VIDPID(0x20ab, 0x55ef),
        MAKE_VIDPID(0x24c6, 0x541a),
        MAKE_VIDPID(0x24c6, 0x542a),
        MAKE_VIDPID(0x24c6, 0x543a),
        MAKE_VIDPID(0x24c6, 0x5509),
        MAKE_VIDPID(0x24c6, 0x551a),
        MAKE_VIDPID(0x24c6, 0x561a),
        MAKE_VIDPID(0x24c6, 0x581a),
        MAKE_VIDPID(0x24c6, 0x591a),
        MAKE_VIDPID(0x24c6, 0x592a),
        MAKE_VIDPID(0x24c6, 0x791a),
        MAKE_VIDPID(0x2516, 0x0069),
        MAKE_VIDPID(0x25b1, 0x0360),
        MAKE_VIDPID(0x2c22, 0x2203),
        MAKE_VIDPID(0x2e24, 0x0652),
        MAKE_VIDPID(0x2e24, 0x1618),
        MAKE_VIDPID(0x2e24, 0x1688),
        MAKE_VIDPID(0x2f24, 0x0011),
        MAKE_VIDPID(0x2f24, 0x002e),
        MAKE_VIDPID(0x2f24, 0x0050),
        MAKE_VIDPID(0x2f24, 0x0053),
        MAKE_VIDPID(0x2f24, 0x008f),
        MAKE_VIDPID(0x2f24, 0x0091),
        MAKE_VIDPID(0x2f24, 0x00b7),
        MAKE_VIDPID(0xd2d2, 0xd2d2),
};

EControllerType GuessControllerType( int nVID, int nPID )
{
    unsigned int unDeviceID = MAKE_CONTROLLER_ID( nVID, nPID );
    int iIndex;

    for ( iIndex = 0; iIndex < sizeof( arrControllers ) / sizeof( arrControllers[0] ); ++iIndex )
    {
        if ( unDeviceID == arrControllers[ iIndex ].m_unDeviceID )
        {
            return arrControllers[ iIndex ].m_eControllerType;
        }
    }

    return k_eControllerType_UnknownNonSteamController;
}

bool SDL_IsJoystickXboxOne(Uint16 vendor_id, Uint16 product_id)
{
    EControllerType eType = GuessControllerType(vendor_id, product_id);
    return eType == k_eControllerType_XBoxOneController ||
           eType == k_eControllerType_XBoxEliteController;
}

bool SDL_IsJoystickXboxOneElite(Uint16 vendor_id, Uint16 product_id)
{
    EControllerType eType = GuessControllerType(vendor_id, product_id);
    return eType == k_eControllerType_XBoxEliteController;
}

bool SDL_IsJoystickXboxSeriesX(Uint16 vendor_id, Uint16 product_id)
{
    if (!SDL_IsJoystickXboxOne(vendor_id, product_id)) {
        return false;
    }

    // Most new controllers have the share button, so we'll default to true and
    // have a list of older XBox One controllers that are known not to have it.
    Uint32 vidpid = MAKE_VIDPID(vendor_id, product_id);
    for (int i = 0; i < (sizeof(initial_old_xboxone_controllers) / sizeof(*initial_old_xboxone_controllers)); i++) {
        if (vidpid == initial_old_xboxone_controllers[i]) {
            return false;
        }
    }

    return true;
}

bool SDL_IsJoystickDualSenseEdge(Uint16 vendor_id, Uint16 product_id)
{
    EControllerType eType = GuessControllerType(vendor_id, product_id);
    return eType == k_eControllerType_PS5EdgeController;
}