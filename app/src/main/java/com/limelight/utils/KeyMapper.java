package com.limelight.utils;

public class KeyMapper {
    /* Linux Key Codes 
     * From https://github.com/torvalds/linux/blob/master/include/uapi/linux/input-event-codes.h
    */
    public static int KEY_RESERVED = 0;   
    public static int KEY_ESC = 1;
    public static int KEY_1 = 2;
    public static int KEY_2 = 3;
    public static int KEY_3 = 4;
    public static int KEY_4 = 5;
    public static int KEY_5 = 6;
    public static int KEY_6 = 7;
    public static int KEY_7 = 8;
    public static int KEY_8 = 9;
    public static int KEY_9 = 10;
    public static int KEY_0 = 11;
    public static int KEY_MINUS = 12;
    public static int KEY_EQUAL = 13;
    public static int KEY_BACKSPACE = 14;
    public static int KEY_TAB = 15;
    public static int KEY_Q = 16;
    public static int KEY_W = 17;
    public static int KEY_E = 18;
    public static int KEY_R = 19;
    public static int KEY_T = 20;
    public static int KEY_Y = 21;
    public static int KEY_U = 22;
    public static int KEY_I = 23;
    public static int KEY_O = 24;
    public static int KEY_P = 25;
    public static int KEY_LEFTBRACE = 26;
    public static int KEY_RIGHTBRACE = 27;
    public static int KEY_ENTER = 28;
    public static int KEY_LEFTCTRL = 29;
    public static int KEY_A = 30;
    public static int KEY_S = 31;
    public static int KEY_D = 32;
    public static int KEY_F = 33;
    public static int KEY_G = 34;
    public static int KEY_H = 35;
    public static int KEY_J = 36;
    public static int KEY_K = 37;
    public static int KEY_L = 38;
    public static int KEY_SEMICOLON = 39;
    public static int KEY_APOSTROPHE = 40;
    public static int KEY_GRAVE = 41;
    public static int KEY_LEFTSHIFT = 42;
    public static int KEY_BACKSLASH = 43;
    public static int KEY_Z = 44;
    public static int KEY_X = 45;
    public static int KEY_C = 46;
    public static int KEY_V = 47;
    public static int KEY_B = 48;
    public static int KEY_N = 49;
    public static int KEY_M = 50;
    public static int KEY_COMMA = 51;
    public static int KEY_DOT = 52;
    public static int KEY_SLASH = 53;
    public static int KEY_RIGHTSHIFT = 54;
    public static int KEY_KPASTERISK = 55;
    public static int KEY_LEFTALT = 56;
    public static int KEY_SPACE = 57;
    public static int KEY_CAPSLOCK = 58;
    public static int KEY_F1 = 59;
    public static int KEY_F2 = 60;
    public static int KEY_F3 = 61;
    public static int KEY_F4 = 62;
    public static int KEY_F5 = 63;
    public static int KEY_F6 = 64;
    public static int KEY_F7 = 65;
    public static int KEY_F8 = 66;
    public static int KEY_F9 = 67;
    public static int KEY_F10 = 68;
    public static int KEY_NUMLOCK = 69;
    public static int KEY_SCROLLLOCK = 70;
    public static int KEY_KP7 = 71;
    public static int KEY_KP8 = 72;
    public static int KEY_KP9 = 73;
    public static int KEY_KPMINUS = 74;
    public static int KEY_KP4 = 75;
    public static int KEY_KP5 = 76;
    public static int KEY_KP6 = 77;
    public static int KEY_KPPLUS = 78;
    public static int KEY_KP1 = 79;
    public static int KEY_KP2 = 80;
    public static int KEY_KP3 = 81;
    public static int KEY_KP0 = 82;
    public static int KEY_KPDOT = 83;

    public static int KEY_ZENKAKUHANKAKU = 85;
    public static int KEY_102ND = 86;
    public static int KEY_F11 = 87;
    public static int KEY_F12 = 88;
    public static int KEY_RO = 89;
    public static int KEY_KATAKANA = 90;
    public static int KEY_HIRAGANA = 91;
    public static int KEY_HENKAN = 92;
    public static int KEY_KATAKANAHIRAGANA = 93;
    public static int KEY_MUHENKAN = 94;
    public static int KEY_KPJPCOMMA = 95;
    public static int KEY_KPENTER = 96;
    public static int KEY_RIGHTCTRL = 97;
    public static int KEY_KPSLASH = 98;
    public static int KEY_SYSRQ = 99;
    public static int KEY_RIGHTALT = 100;
    public static int KEY_LINEFEED = 101;
    public static int KEY_HOME = 102;
    public static int KEY_UP = 103;
    public static int KEY_PAGEUP = 104;
    public static int KEY_LEFT = 105;
    public static int KEY_RIGHT = 106;
    public static int KEY_END = 107;
    public static int KEY_DOWN = 108;
    public static int KEY_PAGEDOWN = 109;
    public static int KEY_INSERT = 110;
    public static int KEY_DELETE = 111;
    public static int KEY_MACRO = 112;
    public static int KEY_MUTE = 113;
    public static int KEY_VOLUMEDOWN = 114;
    public static int KEY_VOLUMEUP = 115;
    public static int KEY_POWER = 116; 	/* SC System Power Down */
    public static int KEY_KPEQUAL = 117;
    public static int KEY_KPPLUSMINUS = 118;
    public static int KEY_PAUSE = 119;
    public static int KEY_SCALE = 120; 	/* AL Compiz Scale (Expose) */

    public static int KEY_KPCOMMA = 121;
    public static int KEY_HANGEUL = 122;
    public static int KEY_HANGUEL = KEY_HANGEUL;
    public static int KEY_HANJA = 123;
    public static int KEY_YEN = 124;
    public static int KEY_LEFTMETA = 125;
    public static int KEY_RIGHTMETA = 126;
    public static int KEY_COMPOSE = 127;

    public static int KEY_STOP = 128; 	/* AC Stop */
    public static int KEY_AGAIN = 129;
    public static int KEY_PROPS = 130; 	/* AC Properties */
    public static int KEY_UNDO = 131; 	/* AC Undo */
    public static int KEY_FRONT = 132;
    public static int KEY_COPY = 133; 	/* AC Copy */
    public static int KEY_OPEN = 134; 	/* AC Open */
    public static int KEY_PASTE = 135; 	/* AC Paste */
    public static int KEY_FIND = 136; 	/* AC Search */
    public static int KEY_CUT = 137; 	/* AC Cut */
    public static int KEY_HELP = 138; 	/* AL Integrated Help Center */
    public static int KEY_MENU = 139; 	/* Menu (show menu) */
    public static int KEY_CALC = 140; 	/* AL Calculator */
    public static int KEY_SETUP = 141;
    public static int KEY_SLEEP = 142; 	/* SC System Sleep */
    public static int KEY_WAKEUP = 143; 	/* System Wake Up */
    public static int KEY_FILE = 144; 	/* AL Local Machine Browser */
    public static int KEY_SENDFILE = 145;
    public static int KEY_DELETEFILE = 146;
    public static int KEY_XFER = 147;
    public static int KEY_PROG1 = 148;
    public static int KEY_PROG2 = 149;
    public static int KEY_WWW = 150; 	/* AL Internet Browser */
    public static int KEY_MSDOS = 151;
    public static int KEY_COFFEE = 152; 	/* AL Terminal Lock/Screensaver */
    public static int KEY_SCREENLOCK = KEY_COFFEE;
    public static int KEY_ROTATE_DISPLAY = 153; 	/* Display orientation for e.g. tablets */
    public static int KEY_DIRECTION = KEY_ROTATE_DISPLAY;
    public static int KEY_CYCLEWINDOWS = 154;
    public static int KEY_MAIL = 155;
    public static int KEY_BOOKMARKS = 156; 	/* AC Bookmarks */
    public static int KEY_COMPUTER = 157;
    public static int KEY_BACK = 158; 	/* AC Back */
    public static int KEY_FORWARD = 159; 	/* AC Forward */
    public static int KEY_CLOSECD = 160;
    public static int KEY_EJECTCD = 161;
    public static int KEY_EJECTCLOSECD = 162;
    public static int KEY_NEXTSONG = 163;
    public static int KEY_PLAYPAUSE = 164;
    public static int KEY_PREVIOUSSONG = 165;
    public static int KEY_STOPCD = 166;
    public static int KEY_RECORD = 167;
    public static int KEY_REWIND = 168;
    public static int KEY_PHONE = 169; 	/* Media Select Telephone */
    public static int KEY_ISO = 170;
    public static int KEY_CONFIG = 171; 	/* AL Consumer Control Configuration */
    public static int KEY_HOMEPAGE = 172; 	/* AC Home */
    public static int KEY_REFRESH = 173; 	/* AC Refresh */
    public static int KEY_EXIT = 174; 	/* AC Exit */
    public static int KEY_MOVE = 175;
    public static int KEY_EDIT = 176;
    public static int KEY_SCROLLUP = 177;
    public static int KEY_SCROLLDOWN = 178;
    public static int KEY_KPLEFTPAREN = 179;
    public static int KEY_KPRIGHTPAREN = 180;
    public static int KEY_NEW = 181; 	/* AC New */
    public static int KEY_REDO = 182; 	/* AC Redo/Repeat */

    public static int KEY_F13 = 183;
    public static int KEY_F14 = 184;
    public static int KEY_F15 = 185;
    public static int KEY_F16 = 186;
    public static int KEY_F17 = 187;
    public static int KEY_F18 = 188;
    public static int KEY_F19 = 189;
    public static int KEY_F20 = 190;
    public static int KEY_F21 = 191;
    public static int KEY_F22 = 192;
    public static int KEY_F23 = 193;
    public static int KEY_F24 = 194;

    public static int KEY_PLAYCD = 200;
    public static int KEY_PAUSECD = 201;
    public static int KEY_PROG3 = 202;
    public static int KEY_PROG4 = 203;
    public static int KEY_ALL_APPLICATIONS = 204; 	/* AC Desktop Show All Applications */
    public static int KEY_DASHBOARD = KEY_ALL_APPLICATIONS;
    public static int KEY_SUSPEND = 205;
    public static int KEY_CLOSE = 206; 	/* AC Close */
    public static int KEY_PLAY = 207;
    public static int KEY_FASTFORWARD = 208;
    public static int KEY_BASSBOOST = 209;
    public static int KEY_PRINT = 210; 	/* AC Print */
    public static int KEY_HP = 211;
    public static int KEY_CAMERA = 212;
    public static int KEY_SOUND = 213;
    public static int KEY_QUESTION = 214;
    public static int KEY_EMAIL = 215;
    public static int KEY_CHAT = 216;
    public static int KEY_SEARCH = 217;
    public static int KEY_CONNECT = 218;
    public static int KEY_FINANCE = 219; 	/* AL Checkbook/Finance */
    public static int KEY_SPORT = 220;
    public static int KEY_SHOP = 221;
    public static int KEY_ALTERASE = 222;
    public static int KEY_CANCEL = 223; 	/* AC Cancel */
    public static int KEY_BRIGHTNESSDOWN = 224;
    public static int KEY_BRIGHTNESSUP = 225;
    public static int KEY_MEDIA = 226;

    public static int KEY_SWITCHVIDEOMODE = 227;	/* Cycle between available video
					   outputs (Monitor/LCD/TV-out/etc) */
    public static int KEY_KBDILLUMTOGGLE = 228;
    public static int KEY_KBDILLUMDOWN = 229;
    public static int KEY_KBDILLUMUP = 230;

    public static int KEY_SEND = 231; 	/* AC Send */
    public static int KEY_REPLY = 232; 	/* AC Reply */
    public static int KEY_FORWARDMAIL = 233; 	/* AC Forward Msg */
    public static int KEY_SAVE = 234; 	/* AC Save */
    public static int KEY_DOCUMENTS = 235;

    public static int KEY_BATTERY = 236;

    public static int KEY_BLUETOOTH = 237;
    public static int KEY_WLAN = 238;
    public static int KEY_UWB = 239;

    public static int KEY_UNKNOWN = 240;

    public static int KEY_VIDEO_NEXT = 241;	/* drive next video source */
    public static int KEY_VIDEO_PREV = 242;	/* drive previous video source */
    public static int KEY_BRIGHTNESS_CYCLE = 243;	/* brightness up, after max is min */
    public static int KEY_BRIGHTNESS_AUTO = 244;	/* Set Auto Brightness: manual
					  brightness control is off,
					  rely on ambient */
    public static int KEY_BRIGHTNESS_ZERO = KEY_BRIGHTNESS_AUTO;
    public static int KEY_DISPLAY_OFF = 245; 	/* display device to off state */

    public static int KEY_WWAN = 246; 	/* Wireless WAN (LTE, UMTS, GSM, etc.) */
    public static int KEY_WIMAX = KEY_WWAN;
    public static int KEY_RFKILL = 247; 	/* Key that controls all radios */

    public static int KEY_MICMUTE = 248; 	/* Mute / unmute the microphone */

/* Code 255 is reserved for special needs of AT keyboard driver */

    public static int BTN_MISC = 0x100;
    public static int BTN_0 = 0x100;
    public static int BTN_1 = 0x101;
    public static int BTN_2 = 0x102;
    public static int BTN_3 = 0x103;
    public static int BTN_4 = 0x104;
    public static int BTN_5 = 0x105;
    public static int BTN_6 = 0x106;
    public static int BTN_7 = 0x107;
    public static int BTN_8 = 0x108;
    public static int BTN_9 = 0x109;

    public static int BTN_MOUSE = 0x110;
    public static int BTN_LEFT = 0x110;
    public static int BTN_RIGHT = 0x111;
    public static int BTN_MIDDLE = 0x112;
    public static int BTN_SIDE = 0x113;
    public static int BTN_EXTRA = 0x114;
    public static int BTN_FORWARD = 0x115;
    public static int BTN_BACK = 0x116;
    public static int BTN_TASK = 0x117;

    public static int BTN_JOYSTICK = 0x120;
    public static int BTN_TRIGGER = 0x120;
    public static int BTN_THUMB = 0x121;
    public static int BTN_THUMB2 = 0x122;
    public static int BTN_TOP = 0x123;
    public static int BTN_TOP2 = 0x124;
    public static int BTN_PINKIE = 0x125;
    public static int BTN_BASE = 0x126;
    public static int BTN_BASE2 = 0x127;
    public static int BTN_BASE3 = 0x128;
    public static int BTN_BASE4 = 0x129;
    public static int BTN_BASE5 = 0x12a;
    public static int BTN_BASE6 = 0x12b;
    public static int BTN_DEAD = 0x12f;

    public static int BTN_GAMEPAD = 0x130;
    public static int BTN_SOUTH = 0x130;
    public static int BTN_A = BTN_SOUTH;
    public static int BTN_EAST = 0x131;
    public static int BTN_B = BTN_EAST;
    public static int BTN_C = 0x132;
    public static int BTN_NORTH = 0x133;
    public static int BTN_X = BTN_NORTH;
    public static int BTN_WEST = 0x134;
    public static int BTN_Y = BTN_WEST;
    public static int BTN_Z = 0x135;
    public static int BTN_TL = 0x136;
    public static int BTN_TR = 0x137;
    public static int BTN_TL2 = 0x138;
    public static int BTN_TR2 = 0x139;
    public static int BTN_SELECT = 0x13a;
    public static int BTN_START = 0x13b;
    public static int BTN_MODE = 0x13c;
    public static int BTN_THUMBL = 0x13d;
    public static int BTN_THUMBR = 0x13e;

    public static int BTN_DIGI = 0x140;
    public static int BTN_TOOL_PEN = 0x140;
    public static int BTN_TOOL_RUBBER = 0x141;
    public static int BTN_TOOL_BRUSH = 0x142;
    public static int BTN_TOOL_PENCIL = 0x143;
    public static int BTN_TOOL_AIRBRUSH = 0x144;
    public static int BTN_TOOL_FINGER = 0x145;
    public static int BTN_TOOL_MOUSE = 0x146;
    public static int BTN_TOOL_LENS = 0x147;
    public static int BTN_TOOL_QUINTTAP = 0x148; 	/* Five fingers on trackpad */
    public static int BTN_STYLUS3 = 0x149;
    public static int BTN_TOUCH = 0x14a;
    public static int BTN_STYLUS = 0x14b;
    public static int BTN_STYLUS2 = 0x14c;
    public static int BTN_TOOL_DOUBLETAP = 0x14d;
    public static int BTN_TOOL_TRIPLETAP = 0x14e;
    public static int BTN_TOOL_QUADTAP = 0x14f; 	/* Four fingers on trackpad */

    public static int BTN_WHEEL = 0x150;
    public static int BTN_GEAR_DOWN = 0x150;
    public static int BTN_GEAR_UP = 0x151;

    public static int KEY_OK = 0x160;
    public static int KEY_SELECT = 0x161;
    public static int KEY_GOTO = 0x162;
    public static int KEY_CLEAR = 0x163;
    public static int KEY_POWER2 = 0x164;
    public static int KEY_OPTION = 0x165;
    public static int KEY_INFO = 0x166; 	/* AL OEM Features/Tips/Tutorial */
    public static int KEY_TIME = 0x167;
    public static int KEY_VENDOR = 0x168;
    public static int KEY_ARCHIVE = 0x169;
    public static int KEY_PROGRAM = 0x16a; 	/* Media Select Program Guide */
    public static int KEY_CHANNEL = 0x16b;
    public static int KEY_FAVORITES = 0x16c;
    public static int KEY_EPG = 0x16d;
    public static int KEY_PVR = 0x16e; 	/* Media Select Home */
    public static int KEY_MHP = 0x16f;
    public static int KEY_LANGUAGE = 0x170;
    public static int KEY_TITLE = 0x171;
    public static int KEY_SUBTITLE = 0x172;
    public static int KEY_ANGLE = 0x173;
    public static int KEY_FULL_SCREEN = 0x174; 	/* AC View Toggle */
    public static int KEY_ZOOM = KEY_FULL_SCREEN;
    public static int KEY_MODE = 0x175;
    public static int KEY_KEYBOARD = 0x176;
    public static int KEY_ASPECT_RATIO = 0x177; 	/* HUTRR37: Aspect */
    public static int KEY_SCREEN = KEY_ASPECT_RATIO;
    public static int KEY_PC = 0x178; 	/* Media Select Computer */
    public static int KEY_TV = 0x179; 	/* Media Select TV */
    public static int KEY_TV2 = 0x17a; 	/* Media Select Cable */
    public static int KEY_VCR = 0x17b; 	/* Media Select VCR */
    public static int KEY_VCR2 = 0x17c; 	/* VCR Plus */
    public static int KEY_SAT = 0x17d; 	/* Media Select Satellite */
    public static int KEY_SAT2 = 0x17e;
    public static int KEY_CD = 0x17f; 	/* Media Select CD */
    public static int KEY_TAPE = 0x180; 	/* Media Select Tape */
    public static int KEY_RADIO = 0x181;
    public static int KEY_TUNER = 0x182; 	/* Media Select Tuner */
    public static int KEY_PLAYER = 0x183;
    public static int KEY_TEXT = 0x184;
    public static int KEY_DVD = 0x185; 	/* Media Select DVD */
    public static int KEY_AUX = 0x186;
    public static int KEY_MP3 = 0x187;
    public static int KEY_AUDIO = 0x188; 	/* AL Audio Browser */
    public static int KEY_VIDEO = 0x189; 	/* AL Movie Browser */
    public static int KEY_DIRECTORY = 0x18a;
    public static int KEY_LIST = 0x18b;
    public static int KEY_MEMO = 0x18c; 	/* Media Select Messages */
    public static int KEY_CALENDAR = 0x18d;
    public static int KEY_RED = 0x18e;
    public static int KEY_GREEN = 0x18f;
    public static int KEY_YELLOW = 0x190;
    public static int KEY_BLUE = 0x191;
    public static int KEY_CHANNELUP = 0x192; 	/* Channel Increment */
    public static int KEY_CHANNELDOWN = 0x193; 	/* Channel Decrement */
    public static int KEY_FIRST = 0x194;
    public static int KEY_LAST = 0x195; 	/* Recall Last */
    public static int KEY_AB = 0x196;
    public static int KEY_NEXT = 0x197;
    public static int KEY_RESTART = 0x198;
    public static int KEY_SLOW = 0x199;
    public static int KEY_SHUFFLE = 0x19a;
    public static int KEY_BREAK = 0x19b;
    public static int KEY_PREVIOUS = 0x19c;
    public static int KEY_DIGITS = 0x19d;
    public static int KEY_TEEN = 0x19e;
    public static int KEY_TWEN = 0x19f;
    public static int KEY_VIDEOPHONE = 0x1a0; 	/* Media Select Video Phone */
    public static int KEY_GAMES = 0x1a1; 	/* Media Select Games */
    public static int KEY_ZOOMIN = 0x1a2; 	/* AC Zoom In */
    public static int KEY_ZOOMOUT = 0x1a3; 	/* AC Zoom Out */
    public static int KEY_ZOOMRESET = 0x1a4; 	/* AC Zoom */
    public static int KEY_WORDPROCESSOR = 0x1a5; 	/* AL Word Processor */
    public static int KEY_EDITOR = 0x1a6; 	/* AL Text Editor */
    public static int KEY_SPREADSHEET = 0x1a7; 	/* AL Spreadsheet */
    public static int KEY_GRAPHICSEDITOR = 0x1a8; 	/* AL Graphics Editor */
    public static int KEY_PRESENTATION = 0x1a9; 	/* AL Presentation App */
    public static int KEY_DATABASE = 0x1aa; 	/* AL Database App */
    public static int KEY_NEWS = 0x1ab; 	/* AL Newsreader */
    public static int KEY_VOICEMAIL = 0x1ac; 	/* AL Voicemail */
    public static int KEY_ADDRESSBOOK = 0x1ad; 	/* AL Contacts/Address Book */
    public static int KEY_MESSENGER = 0x1ae; 	/* AL Instant Messaging */
    public static int KEY_DISPLAYTOGGLE = 0x1af; 	/* Turn display (LCD) on and off */
    public static int KEY_BRIGHTNESS_TOGGLE = KEY_DISPLAYTOGGLE;
    public static int KEY_SPELLCHECK = 0x1b0;    /* AL Spell Check */
    public static int KEY_LOGOFF = 0x1b1;    /* AL Logoff */

    public static int KEY_DOLLAR = 0x1b2;
    public static int KEY_EURO = 0x1b3;

    public static int KEY_FRAMEBACK = 0x1b4; 	/* Consumer - transport controls */
    public static int KEY_FRAMEFORWARD = 0x1b5;
    public static int KEY_CONTEXT_MENU = 0x1b6; 	/* GenDesc - system context menu */
    public static int KEY_MEDIA_REPEAT = 0x1b7; 	/* Consumer - transport control */
    public static int KEY_10CHANNELSUP = 0x1b8; 	/* 10 channels up (10+) */
    public static int KEY_10CHANNELSDOWN = 0x1b9; 	/* 10 channels down (10-) */
    public static int KEY_IMAGES = 0x1ba; 	/* AL Image Browser */
    public static int KEY_NOTIFICATION_CENTER = 0x1bc; 	/* Show/hide the notification center */
    public static int KEY_PICKUP_PHONE = 0x1bd; 	/* Answer incoming call */
    public static int KEY_HANGUP_PHONE = 0x1be; 	/* Decline incoming call */

    public static int KEY_DEL_EOL = 0x1c0;
    public static int KEY_DEL_EOS = 0x1c1;
    public static int KEY_INS_LINE = 0x1c2;
    public static int KEY_DEL_LINE = 0x1c3;

    public static int KEY_FN = 0x1d0;
    public static int KEY_FN_ESC = 0x1d1;
    public static int KEY_FN_F1 = 0x1d2;
    public static int KEY_FN_F2 = 0x1d3;
    public static int KEY_FN_F3 = 0x1d4;
    public static int KEY_FN_F4 = 0x1d5;
    public static int KEY_FN_F5 = 0x1d6;
    public static int KEY_FN_F6 = 0x1d7;
    public static int KEY_FN_F7 = 0x1d8;
    public static int KEY_FN_F8 = 0x1d9;
    public static int KEY_FN_F9 = 0x1da;
    public static int KEY_FN_F10 = 0x1db;
    public static int KEY_FN_F11 = 0x1dc;
    public static int KEY_FN_F12 = 0x1dd;
    public static int KEY_FN_1 = 0x1de;
    public static int KEY_FN_2 = 0x1df;
    public static int KEY_FN_D = 0x1e0;
    public static int KEY_FN_E = 0x1e1;
    public static int KEY_FN_F = 0x1e2;
    public static int KEY_FN_S = 0x1e3;
    public static int KEY_FN_B = 0x1e4;
    public static int KEY_FN_RIGHT_SHIFT = 0x1e5;

    public static int KEY_BRL_DOT1 = 0x1f1;
    public static int KEY_BRL_DOT2 = 0x1f2;
    public static int KEY_BRL_DOT3 = 0x1f3;
    public static int KEY_BRL_DOT4 = 0x1f4;
    public static int KEY_BRL_DOT5 = 0x1f5;
    public static int KEY_BRL_DOT6 = 0x1f6;
    public static int KEY_BRL_DOT7 = 0x1f7;
    public static int KEY_BRL_DOT8 = 0x1f8;
    public static int KEY_BRL_DOT9 = 0x1f9;
    public static int KEY_BRL_DOT10 = 0x1fa;

    public static int KEY_NUMERIC_0 = 0x200; 	/* used by phones, remote controls, */
    public static int KEY_NUMERIC_1 = 0x201; 	/* and other keypads */
    public static int KEY_NUMERIC_2 = 0x202;
    public static int KEY_NUMERIC_3 = 0x203;
    public static int KEY_NUMERIC_4 = 0x204;
    public static int KEY_NUMERIC_5 = 0x205;
    public static int KEY_NUMERIC_6 = 0x206;
    public static int KEY_NUMERIC_7 = 0x207;
    public static int KEY_NUMERIC_8 = 0x208;
    public static int KEY_NUMERIC_9 = 0x209;
    public static int KEY_NUMERIC_STAR = 0x20a;
    public static int KEY_NUMERIC_POUND = 0x20b;
    public static int KEY_NUMERIC_A = 0x20c; 	/* Phone key A - HUT Telephony 0xb9 */
    public static int KEY_NUMERIC_B = 0x20d;
    public static int KEY_NUMERIC_C = 0x20e;
    public static int KEY_NUMERIC_D = 0x20f;

    public static int KEY_CAMERA_FOCUS = 0x210;
    public static int KEY_WPS_BUTTON = 0x211; 	/* WiFi Protected Setup key */

    public static int KEY_TOUCHPAD_TOGGLE = 0x212; 	/* Request switch touchpad on or off */
    public static int KEY_TOUCHPAD_ON = 0x213;
    public static int KEY_TOUCHPAD_OFF = 0x214;

    public static int KEY_CAMERA_ZOOMIN = 0x215;
    public static int KEY_CAMERA_ZOOMOUT = 0x216;
    public static int KEY_CAMERA_UP = 0x217;
    public static int KEY_CAMERA_DOWN = 0x218;
    public static int KEY_CAMERA_LEFT = 0x219;
    public static int KEY_CAMERA_RIGHT = 0x21a;

    public static int KEY_ATTENDANT_ON = 0x21b;
    public static int KEY_ATTENDANT_OFF = 0x21c;
    public static int KEY_ATTENDANT_TOGGLE = 0x21d; 	/* Attendant call on or off */
    public static int KEY_LIGHTS_TOGGLE = 0x21e; 	/* Reading light on or off */

    public static int BTN_DPAD_UP = 0x220;
    public static int BTN_DPAD_DOWN = 0x221;
    public static int BTN_DPAD_LEFT = 0x222;
    public static int BTN_DPAD_RIGHT = 0x223;

    public static int KEY_ALS_TOGGLE = 0x230; 	/* Ambient light sensor */
    public static int KEY_ROTATE_LOCK_TOGGLE = 0x231; 	/* Display rotation lock */
    public static int KEY_REFRESH_RATE_TOGGLE = 0x232; 	/* Display refresh rate toggle */

    public static int KEY_BUTTONCONFIG = 0x240; 	/* AL Button Configuration */
    public static int KEY_TASKMANAGER = 0x241; 	/* AL Task/Project Manager */
    public static int KEY_JOURNAL = 0x242; 	/* AL Log/Journal/Timecard */
    public static int KEY_CONTROLPANEL = 0x243; 	/* AL Control Panel */
    public static int KEY_APPSELECT = 0x244; 	/* AL Select Task/Application */
    public static int KEY_SCREENSAVER = 0x245; 	/* AL Screen Saver */
    public static int KEY_VOICECOMMAND = 0x246; 	/* Listening Voice Command */
    public static int KEY_ASSISTANT = 0x247; 	/* AL Context-aware desktop assistant */
    public static int KEY_KBD_LAYOUT_NEXT = 0x248; 	/* AC Next Keyboard Layout Select */
    public static int KEY_EMOJI_PICKER = 0x249; 	/* Show/hide emoji picker (HUTRR101) */
    public static int KEY_DICTATE = 0x24a; 	/* Start or Stop Voice Dictation Session (HUTRR99) */
    public static int KEY_CAMERA_ACCESS_ENABLE = 0x24b; 	/* Enables programmatic access to camera devices. (HUTRR72) */
    public static int KEY_CAMERA_ACCESS_DISABLE = 0x24c; 	/* Disables programmatic access to camera devices. (HUTRR72) */
    public static int KEY_CAMERA_ACCESS_TOGGLE = 0x24d; 	/* Toggles the current state of the camera access control. (HUTRR72) */
    public static int KEY_ACCESSIBILITY = 0x24e; 	/* Toggles the system bound accessibility UI/command (HUTRR116) */
    public static int KEY_DO_NOT_DISTURB = 0x24f; 	/* Toggles the system-wide "Do Not Disturb" control (HUTRR94)*/

    public static int KEY_BRIGHTNESS_MIN = 0x250; 	/* Set Brightness to Minimum */
    public static int KEY_BRIGHTNESS_MAX = 0x251; 	/* Set Brightness to Maximum */

    public static int KEY_KBDINPUTASSIST_PREV = 0x260;
    public static int KEY_KBDINPUTASSIST_NEXT = 0x261;
    public static int KEY_KBDINPUTASSIST_PREVGROUP = 0x262;
    public static int KEY_KBDINPUTASSIST_NEXTGROUP = 0x263;
    public static int KEY_KBDINPUTASSIST_ACCEPT = 0x264;
    public static int KEY_KBDINPUTASSIST_CANCEL = 0x265;

/* Diagonal movement keys */
    public static int KEY_RIGHT_UP = 0x266;
    public static int KEY_RIGHT_DOWN = 0x267;
    public static int KEY_LEFT_UP = 0x268;
    public static int KEY_LEFT_DOWN = 0x269;

    public static int KEY_ROOT_MENU = 0x26a;  /* Show Device's Root Menu */
/* Show Top Menu of the Media (e.g. DVD) */
    public static int KEY_MEDIA_TOP_MENU = 0x26b;
    public static int KEY_NUMERIC_11 = 0x26c;
    public static int KEY_NUMERIC_12 = 0x26d;
/*
 * Toggle Audio Description: refers to an audio service that helps blind and
 * visually impaired consumers understand the action in a program. Note: in
 * some countries this is referred to as "Video Description".
 */
    public static int KEY_AUDIO_DESC = 0x26e;
    public static int KEY_3D_MODE = 0x26f;
    public static int KEY_NEXT_FAVORITE = 0x270;
    public static int KEY_STOP_RECORD = 0x271;
    public static int KEY_PAUSE_RECORD = 0x272;
    public static int KEY_VOD = 0x273;  /* Video on Demand */
    public static int KEY_UNMUTE = 0x274;
    public static int KEY_FASTREVERSE = 0x275;
    public static int KEY_SLOWREVERSE = 0x276;
/*
 * Control a data application associated with the currently viewed channel,
 * e.g. teletext or data broadcast application (MHEG, MHP, HbbTV, etc.)
 */
    public static int KEY_DATA = 0x277;
    public static int KEY_ONSCREEN_KEYBOARD = 0x278;
/* Electronic privacy screen control */
    public static int KEY_PRIVACY_SCREEN_TOGGLE = 0x279;

/* Select an area of screen to be copied */
    public static int KEY_SELECTIVE_SCREENSHOT = 0x27a;

/* Move the focus to the next or previous user controllable element within a UI container */
    public static int KEY_NEXT_ELEMENT = 0x27b;
    public static int KEY_PREVIOUS_ELEMENT = 0x27c;

/* Toggle Autopilot engagement */
    public static int KEY_AUTOPILOT_ENGAGE_TOGGLE = 0x27d;

/* Shortcut Keys */
    public static int KEY_MARK_WAYPOINT = 0x27e;
    public static int KEY_SOS = 0x27f;
    public static int KEY_NAV_CHART = 0x280;
    public static int KEY_FISHING_CHART = 0x281;
    public static int KEY_SINGLE_RANGE_RADAR = 0x282;
    public static int KEY_DUAL_RANGE_RADAR = 0x283;
    public static int KEY_RADAR_OVERLAY = 0x284;
    public static int KEY_TRADITIONAL_SONAR = 0x285;
    public static int KEY_CLEARVU_SONAR = 0x286;
    public static int KEY_SIDEVU_SONAR = 0x287;
    public static int KEY_NAV_INFO = 0x288;
    public static int KEY_BRIGHTNESS_MENU = 0x289;

/*
 * Some keyboards have keys which do not have a defined meaning, these keys
 * are intended to be programmed / bound to macros by the user. For most
 * keyboards with these macro-keys the key-sequence to inject, or action to
 * take, is all handled by software on the host side. So from the kernel's
 * point of view these are just normal keys.
 *
 * The KEY_MACRO# codes below are intended for such keys, which may be labeled
 * e.g. G1-G18, or S1 - S30. The KEY_MACRO# codes MUST NOT be used for keys
 * where the marking on the key does indicate a defined meaning / purpose.
 *
 * The KEY_MACRO# codes MUST also NOT be used as fallback for when no existing
 * KEY_FOO define matches the marking / purpose. In this case a new KEY_FOO
 * define MUST be added.
 */
    public static int KEY_MACRO1 = 0x290;
    public static int KEY_MACRO2 = 0x291;
    public static int KEY_MACRO3 = 0x292;
    public static int KEY_MACRO4 = 0x293;
    public static int KEY_MACRO5 = 0x294;
    public static int KEY_MACRO6 = 0x295;
    public static int KEY_MACRO7 = 0x296;
    public static int KEY_MACRO8 = 0x297;
    public static int KEY_MACRO9 = 0x298;
    public static int KEY_MACRO10 = 0x299;
    public static int KEY_MACRO11 = 0x29a;
    public static int KEY_MACRO12 = 0x29b;
    public static int KEY_MACRO13 = 0x29c;
    public static int KEY_MACRO14 = 0x29d;
    public static int KEY_MACRO15 = 0x29e;
    public static int KEY_MACRO16 = 0x29f;
    public static int KEY_MACRO17 = 0x2a0;
    public static int KEY_MACRO18 = 0x2a1;
    public static int KEY_MACRO19 = 0x2a2;
    public static int KEY_MACRO20 = 0x2a3;
    public static int KEY_MACRO21 = 0x2a4;
    public static int KEY_MACRO22 = 0x2a5;
    public static int KEY_MACRO23 = 0x2a6;
    public static int KEY_MACRO24 = 0x2a7;
    public static int KEY_MACRO25 = 0x2a8;
    public static int KEY_MACRO26 = 0x2a9;
    public static int KEY_MACRO27 = 0x2aa;
    public static int KEY_MACRO28 = 0x2ab;
    public static int KEY_MACRO29 = 0x2ac;
    public static int KEY_MACRO30 = 0x2ad;

/*
 * Some keyboards with the macro-keys described above have some extra keys
 * for controlling the host-side software responsible for the macro handling:
 * -A macro recording start/stop key. Note that not all keyboards which emit
 *  KEY_MACRO_RECORD_START will also emit KEY_MACRO_RECORD_STOP if
 *  KEY_MACRO_RECORD_STOP is not advertised, then KEY_MACRO_RECORD_START
 *  should be interpreted as a recording start/stop toggle;
 * -Keys for switching between different macro (pre)sets, either a key for
 *  cycling through the configured presets or keys to directly select a preset.
 */
    public static int KEY_MACRO_RECORD_START = 0x2b0;
    public static int KEY_MACRO_RECORD_STOP = 0x2b1;
    public static int KEY_MACRO_PRESET_CYCLE = 0x2b2;
    public static int KEY_MACRO_PRESET1 = 0x2b3;
    public static int KEY_MACRO_PRESET2 = 0x2b4;
    public static int KEY_MACRO_PRESET3 = 0x2b5;

/*
 * Some keyboards have a buildin LCD panel where the contents are controlled
 * by the host. Often these have a number of keys directly below the LCD
 * intended for controlling a menu shown on the LCD. These keys often don't
 * have any labeling so we just name them KEY_KBD_LCD_MENU#
 */
    public static int KEY_KBD_LCD_MENU1 = 0x2b8;
    public static int KEY_KBD_LCD_MENU2 = 0x2b9;
    public static int KEY_KBD_LCD_MENU3 = 0x2ba;
    public static int KEY_KBD_LCD_MENU4 = 0x2bb;
    public static int KEY_KBD_LCD_MENU5 = 0x2bc;

    public static int BTN_TRIGGER_HAPPY = 0x2c0;
    public static int BTN_TRIGGER_HAPPY1 = 0x2c0;
    public static int BTN_TRIGGER_HAPPY2 = 0x2c1;
    public static int BTN_TRIGGER_HAPPY3 = 0x2c2;
    public static int BTN_TRIGGER_HAPPY4 = 0x2c3;
    public static int BTN_TRIGGER_HAPPY5 = 0x2c4;
    public static int BTN_TRIGGER_HAPPY6 = 0x2c5;
    public static int BTN_TRIGGER_HAPPY7 = 0x2c6;
    public static int BTN_TRIGGER_HAPPY8 = 0x2c7;
    public static int BTN_TRIGGER_HAPPY9 = 0x2c8;
    public static int BTN_TRIGGER_HAPPY10 = 0x2c9;
    public static int BTN_TRIGGER_HAPPY11 = 0x2ca;
    public static int BTN_TRIGGER_HAPPY12 = 0x2cb;
    public static int BTN_TRIGGER_HAPPY13 = 0x2cc;
    public static int BTN_TRIGGER_HAPPY14 = 0x2cd;
    public static int BTN_TRIGGER_HAPPY15 = 0x2ce;
    public static int BTN_TRIGGER_HAPPY16 = 0x2cf;
    public static int BTN_TRIGGER_HAPPY17 = 0x2d0;
    public static int BTN_TRIGGER_HAPPY18 = 0x2d1;
    public static int BTN_TRIGGER_HAPPY19 = 0x2d2;
    public static int BTN_TRIGGER_HAPPY20 = 0x2d3;
    public static int BTN_TRIGGER_HAPPY21 = 0x2d4;
    public static int BTN_TRIGGER_HAPPY22 = 0x2d5;
    public static int BTN_TRIGGER_HAPPY23 = 0x2d6;
    public static int BTN_TRIGGER_HAPPY24 = 0x2d7;
    public static int BTN_TRIGGER_HAPPY25 = 0x2d8;
    public static int BTN_TRIGGER_HAPPY26 = 0x2d9;
    public static int BTN_TRIGGER_HAPPY27 = 0x2da;
    public static int BTN_TRIGGER_HAPPY28 = 0x2db;
    public static int BTN_TRIGGER_HAPPY29 = 0x2dc;
    public static int BTN_TRIGGER_HAPPY30 = 0x2dd;
    public static int BTN_TRIGGER_HAPPY31 = 0x2de;
    public static int BTN_TRIGGER_HAPPY32 = 0x2df;
    public static int BTN_TRIGGER_HAPPY33 = 0x2e0;
    public static int BTN_TRIGGER_HAPPY34 = 0x2e1;
    public static int BTN_TRIGGER_HAPPY35 = 0x2e2;
    public static int BTN_TRIGGER_HAPPY36 = 0x2e3;
    public static int BTN_TRIGGER_HAPPY37 = 0x2e4;
    public static int BTN_TRIGGER_HAPPY38 = 0x2e5;
    public static int BTN_TRIGGER_HAPPY39 = 0x2e6;
    public static int BTN_TRIGGER_HAPPY40 = 0x2e7;

/* We avoid low common keys in module aliases so they don't get huge. */
    public static int KEY_MIN_INTERESTING = KEY_MUTE;
    public static int KEY_MAX = 0x2ff;
    public static int KEY_CNT = (KEY_MAX+1);


/* Windows Virtual Key Codes
 * From https://learn.microsoft.com/en-us/windows/win32/inputdev/virtual-key-codes
*/
    public static int VK_LBUTTON = 0x01;    // Left mouse button
    public static int VK_RBUTTON = 0x02;    // Right mouse button
    public static int VK_CANCEL = 0x03;    // Control-break processing
    public static int VK_MBUTTON = 0x04;    // Middle mouse button
    public static int VK_XBUTTON1 = 0x05;    // X1 mouse button
    public static int VK_XBUTTON2 = 0x06;    // X2 mouse button
    public static int VK_BACK = 0x08;    // BACKSPACE key
    public static int VK_TAB = 0x09;    // TAB key
    public static int VK_CLEAR = 0x0C;    // CLEAR key
    public static int VK_RETURN = 0x0D;    // ENTER key
    public static int VK_SHIFT = 0x10;    // SHIFT key
    public static int VK_CONTROL = 0x11;    // CTRL key
    public static int VK_MENU = 0x12;    // ALT key
    public static int VK_PAUSE = 0x13;    // PAUSE key
    public static int VK_CAPITAL = 0x14;    // CAPS LOCK key
    public static int VK_KANA = 0x15;    // IME Kana mode
    public static int VK_HANGUL = 0x15;    // IME Hangul mode
    public static int VK_IME_ON = 0x16;    // IME On
    public static int VK_JUNJA = 0x17;    // IME Junja mode
    public static int VK_FINAL = 0x18;    // IME mode
    public static int VK_HANJA = 0x19;    // IME Hanja mode
    public static int VK_KANJI = 0x19;    // IME Kanji mode
    public static int VK_IME_OFF = 0x1A;    // IME Off
    public static int VK_ESCAPE = 0x1B;    // ESC key
    public static int VK_CONVERT = 0x1C;    // IME convert
    public static int VK_NONCONVERT = 0x1D;    // IME nonconvert
    public static int VK_ACCEPT = 0x1E;    // IME accept
    public static int VK_MODECHANGE = 0x1F;    // IME mode change request
    public static int VK_SPACE = 0x20;    // SPACEBAR
    public static int VK_PRIOR = 0x21;    // PAGE UP key
    public static int VK_NEXT = 0x22;    // PAGE DOWN key
    public static int VK_END = 0x23;    // END key
    public static int VK_HOME = 0x24;    // HOME key
    public static int VK_LEFT = 0x25;    // LEFT ARROW key
    public static int VK_UP = 0x26;    // UP ARROW key
    public static int VK_RIGHT = 0x27;    // RIGHT ARROW key
    public static int VK_DOWN = 0x28;    // DOWN ARROW key
    public static int VK_SELECT = 0x29;    // SELECT key
    public static int VK_PRINT = 0x2A;    // PRINT key
    public static int VK_EXECUTE = 0x2B;    // EXECUTE key
    public static int VK_SNAPSHOT = 0x2C;    // PRINT SCREEN key
    public static int VK_INSERT = 0x2D;    // INS key
    public static int VK_DELETE = 0x2E;    // DEL key
    public static int VK_HELP = 0x2F;    // HELP key
    public static int VK_0 = 0x30;    // 0 key
    public static int VK_1 = 0x31;    // 1 key
    public static int VK_2 = 0x32;    // 2 key
    public static int VK_3 = 0x33;    // 3 key
    public static int VK_4 = 0x34;    // 4 key
    public static int VK_5 = 0x35;    // 5 key
    public static int VK_6 = 0x36;    // 6 key
    public static int VK_7 = 0x37;    // 7 key
    public static int VK_8 = 0x38;    // 8 key
    public static int VK_9 = 0x39;    // 9 key
    // 0x3A-40 Undefined
    public static int VK_A = 0x41;    // A key
    public static int VK_B = 0x42;    // B key
    public static int VK_C = 0x43;    // C key
    public static int VK_D = 0x44;    // D key
    public static int VK_E = 0x45;    // E key
    public static int VK_F = 0x46;    // F key
    public static int VK_G = 0x47;    // G key
    public static int VK_H = 0x48;    // H key
    public static int VK_I = 0x49;    // I key
    public static int VK_J = 0x4A;    // J key
    public static int VK_K = 0x4B;    // K key
    public static int VK_L = 0x4C;    // L key
    public static int VK_M = 0x4D;    // M key
    public static int VK_N = 0x4E;    // N key
    public static int VK_O = 0x4F;    // O key
    public static int VK_P = 0x50;    // P key
    public static int VK_Q = 0x51;    // Q key
    public static int VK_R = 0x52;    // R key
    public static int VK_S = 0x53;    // S key
    public static int VK_T = 0x54;    // T key
    public static int VK_U = 0x55;    // U key
    public static int VK_V = 0x56;    // V key
    public static int VK_W = 0x57;    // W key
    public static int VK_X = 0x58;    // X key
    public static int VK_Y = 0x59;    // Y key
    public static int VK_Z = 0x5A;    // Z key
    public static int VK_LWIN = 0x5B;    // Left Windows key
    public static int VK_RWIN = 0x5C;    // Right Windows key
    public static int VK_APPS = 0x5D;    // Applications key
    // 0x5E Reserved
    public static int VK_SLEEP = 0x5F;    // Computer Sleep key
    public static int VK_NUMPAD0 = 0x60;    // Numeric keypad 0 key
    public static int VK_NUMPAD1 = 0x61;    // Numeric keypad 1 key
    public static int VK_NUMPAD2 = 0x62;    // Numeric keypad 2 key
    public static int VK_NUMPAD3 = 0x63;    // Numeric keypad 3 key
    public static int VK_NUMPAD4 = 0x64;    // Numeric keypad 4 key
    public static int VK_NUMPAD5 = 0x65;    // Numeric keypad 5 key
    public static int VK_NUMPAD6 = 0x66;    // Numeric keypad 6 key
    public static int VK_NUMPAD7 = 0x67;    // Numeric keypad 7 key
    public static int VK_NUMPAD8 = 0x68;    // Numeric keypad 8 key
    public static int VK_NUMPAD9 = 0x69;    // Numeric keypad 9 key
    public static int VK_MULTIPLY = 0x6A;    // Multiply key
    public static int VK_ADD = 0x6B;    // Add key
    public static int VK_SEPARATOR = 0x6C;    // Separator key
    public static int VK_SUBTRACT = 0x6D;    // Subtract key
    public static int VK_DECIMAL = 0x6E;    // Decimal key
    public static int VK_DIVIDE = 0x6F;    // Divide key
    public static int VK_F1 = 0x70;    // F1 key
    public static int VK_F2 = 0x71;    // F2 key
    public static int VK_F3 = 0x72;    // F3 key
    public static int VK_F4 = 0x73;    // F4 key
    public static int VK_F5 = 0x74;    // F5 key
    public static int VK_F6 = 0x75;    // F6 key
    public static int VK_F7 = 0x76;    // F7 key
    public static int VK_F8 = 0x77;    // F8 key
    public static int VK_F9 = 0x78;    // F9 key
    public static int VK_F10 = 0x79;    // F10 key
    public static int VK_F11 = 0x7A;    // F11 key
    public static int VK_F12 = 0x7B;    // F12 key
    public static int VK_F13 = 0x7C;    // F13 key
    public static int VK_F14 = 0x7D;    // F14 key
    public static int VK_F15 = 0x7E;    // F15 key
    public static int VK_F16 = 0x7F;    // F16 key
    public static int VK_F17 = 0x80;    // F17 key
    public static int VK_F18 = 0x81;    // F18 key
    public static int VK_F19 = 0x82;    // F19 key
    public static int VK_F20 = 0x83;    // F20 key
    public static int VK_F21 = 0x84;    // F21 key
    public static int VK_F22 = 0x85;    // F22 key
    public static int VK_F23 = 0x86;    // F23 key
    public static int VK_F24 = 0x87;    // F24 key
    // 0x88-8F Reserved
    public static int VK_NUMLOCK = 0x90;    // NUM LOCK key
    public static int VK_SCROLL = 0x91;    // SCROLL LOCK key
    // 0x92-96 OEM specific
    // 0x97-9F Unassigned
    public static int VK_LSHIFT = 0xA0;    // Left SHIFT key
    public static int VK_RSHIFT = 0xA1;    // Right SHIFT key
    public static int VK_LCONTROL = 0xA2;    // Left CONTROL key
    public static int VK_RCONTROL = 0xA3;    // Right CONTROL key
    public static int VK_LMENU = 0xA4;    // Left ALT key
    public static int VK_RMENU = 0xA5;    // Right ALT key
    public static int VK_BROWSER_BACK = 0xA6;    // Browser Back key
    public static int VK_BROWSER_FORWARD = 0xA7;    // Browser Forward key
    public static int VK_BROWSER_REFRESH = 0xA8;    // Browser Refresh key
    public static int VK_BROWSER_STOP = 0xA9;    // Browser Stop key
    public static int VK_BROWSER_SEARCH = 0xAA;    // Browser Search key
    public static int VK_BROWSER_FAVORITES = 0xAB;    // Browser Favorites key
    public static int VK_BROWSER_HOME = 0xAC;    // Browser Start and Home key
    public static int VK_VOLUME_MUTE = 0xAD;    // Volume Mute key
    public static int VK_VOLUME_DOWN = 0xAE;    // Volume Down key
    public static int VK_VOLUME_UP = 0xAF;    // Volume Up key
    public static int VK_MEDIA_NEXT_TRACK = 0xB0;    // Next Track key
    public static int VK_MEDIA_PREV_TRACK = 0xB1;    // Previous Track key
    public static int VK_MEDIA_STOP = 0xB2;    // Stop Media key
    public static int VK_MEDIA_PLAY_PAUSE = 0xB3;    // Play/Pause Media key
    public static int VK_LAUNCH_MAIL = 0xB4;    // Start Mail key
    public static int VK_LAUNCH_MEDIA_SELECT = 0xB5;    // Select Media key
    public static int VK_LAUNCH_APP1 = 0xB6;    // Start Application 1 key
    public static int VK_LAUNCH_APP2 = 0xB7;    // Start Application 2 key
    // 0xB8-B9 Reserved
    public static int VK_OEM_1 = 0xBA;    // Used for miscellaneous characters; it can vary by keyboard. For the US standard keyboard, the ;: key
    public static int VK_OEM_PLUS = 0xBB;    // For any country/region, the + key
    public static int VK_OEM_COMMA = 0xBC;    // For any country/region, the , key
    public static int VK_OEM_MINUS = 0xBD;    // For any country/region, the - key
    public static int VK_OEM_PERIOD = 0xBE;    // For any country/region, the . key
    public static int VK_OEM_2 = 0xBF;    // Used for miscellaneous characters; it can vary by keyboard. For the US standard keyboard, the /? key
    public static int VK_OEM_3 = 0xC0;    // Used for miscellaneous characters; it can vary by keyboard. For the US standard keyboard, the `~ key
    // 0xC1-DA Reserved
    public static int VK_OEM_4 = 0xDB;    // Used for miscellaneous characters; it can vary by keyboard. For the US standard keyboard, the [{ key
    public static int VK_OEM_5 = 0xDC;    // Used for miscellaneous characters; it can vary by keyboard. For the US standard keyboard, the \| key
    public static int VK_OEM_6 = 0xDD;    // Used for miscellaneous characters; it can vary by keyboard. For the US standard keyboard, the ]} key
    public static int VK_OEM_7 = 0xDE;    // Used for miscellaneous characters; it can vary by keyboard. For the US standard keyboard, the '" key
    public static int VK_OEM_8 = 0xDF;    // Used for miscellaneous characters; it can vary by keyboard.
    // 0xE0 Reserved
    // 0xE1 OEM specific
    public static int VK_OEM_102 = 0xE2;    // The <> keys on the US standard keyboard, or the \| key on the non-US 102-key keyboard
    // 0xE3-E4 OEM specific
    public static int VK_PROCESSKEY = 0xE5;    // IME PROCESS key
    // 0xE6 OEM specific
    public static int VK_PACKET = 0xE7;    // Used to pass Unicode characters as if they were keystrokes. The VK_PACKET key is the low word of a 32-bit Virtual Key value used for non-keyboard input methods. For more information, see Remark in KEYBDINPUT, SendInput, WM_KEYDOWN, and WM_KEYUP
    // 0xE8 Unassigned
    // 0xE9-F5 OEM specific
    public static int VK_ATTN = 0xF6;    // Attn key
    public static int VK_CRSEL = 0xF7;    // CrSel key
    public static int VK_EXSEL = 0xF8;    // ExSel key
    public static int VK_EREOF = 0xF9;    // Erase EOF key
    public static int VK_PLAY = 0xFA;    // Play key
    public static int VK_ZOOM = 0xFB;    // Zoom key
    public static int VK_NONAME = 0xFC;    // Reserved
    public static int VK_PA1 = 0xFD;    // PA1 key
    public static int VK_OEM_CLEAR = 0xFE;    // Clear key

    private static int[] linuxToWindowsKeyMap = new int[KEY_CNT];

    static {
        // Initialize all mappings to -1 (invalid/unmapped)
        for (int i = 0; i < KEY_CNT; i++) {
            linuxToWindowsKeyMap[i] = -1;
        }

        // Define mappings
        linuxToWindowsKeyMap[KEY_ESC] = VK_ESCAPE;
        linuxToWindowsKeyMap[KEY_1] = VK_1;
        linuxToWindowsKeyMap[KEY_2] = VK_2;
        linuxToWindowsKeyMap[KEY_3] = VK_3;
        linuxToWindowsKeyMap[KEY_4] = VK_4;
        linuxToWindowsKeyMap[KEY_5] = VK_5;
        linuxToWindowsKeyMap[KEY_6] = VK_6;
        linuxToWindowsKeyMap[KEY_7] = VK_7;
        linuxToWindowsKeyMap[KEY_8] = VK_8;
        linuxToWindowsKeyMap[KEY_9] = VK_9;
        linuxToWindowsKeyMap[KEY_0] = VK_0;
        linuxToWindowsKeyMap[KEY_MINUS] = VK_OEM_MINUS;
        linuxToWindowsKeyMap[KEY_EQUAL] = VK_OEM_PLUS;
        linuxToWindowsKeyMap[KEY_BACKSPACE] = VK_BACK;
        linuxToWindowsKeyMap[KEY_TAB] = VK_TAB;
        linuxToWindowsKeyMap[KEY_Q] = VK_Q;
        linuxToWindowsKeyMap[KEY_W] = VK_W;
        linuxToWindowsKeyMap[KEY_E] = VK_E;
        linuxToWindowsKeyMap[KEY_R] = VK_R;
        linuxToWindowsKeyMap[KEY_T] = VK_T;
        linuxToWindowsKeyMap[KEY_Y] = VK_Y;
        linuxToWindowsKeyMap[KEY_U] = VK_U;
        linuxToWindowsKeyMap[KEY_I] = VK_I;
        linuxToWindowsKeyMap[KEY_O] = VK_O;
        linuxToWindowsKeyMap[KEY_P] = VK_P;
        linuxToWindowsKeyMap[KEY_LEFTBRACE] = VK_OEM_4;
        linuxToWindowsKeyMap[KEY_RIGHTBRACE] = VK_OEM_6;
        linuxToWindowsKeyMap[KEY_ENTER] = VK_RETURN;
        linuxToWindowsKeyMap[KEY_A] = VK_A;
        linuxToWindowsKeyMap[KEY_S] = VK_S;
        linuxToWindowsKeyMap[KEY_D] = VK_D;
        linuxToWindowsKeyMap[KEY_F] = VK_F;
        linuxToWindowsKeyMap[KEY_G] = VK_G;
        linuxToWindowsKeyMap[KEY_H] = VK_H;
        linuxToWindowsKeyMap[KEY_J] = VK_J;
        linuxToWindowsKeyMap[KEY_K] = VK_K;
        linuxToWindowsKeyMap[KEY_L] = VK_L;
        linuxToWindowsKeyMap[KEY_SEMICOLON] = VK_OEM_1;
        linuxToWindowsKeyMap[KEY_APOSTROPHE] = VK_OEM_7;
        linuxToWindowsKeyMap[KEY_GRAVE] = VK_OEM_3;
        linuxToWindowsKeyMap[KEY_LEFTCTRL] = VK_LCONTROL;
        linuxToWindowsKeyMap[KEY_RIGHTCTRL] = VK_RCONTROL;
        linuxToWindowsKeyMap[KEY_LEFTSHIFT] = VK_LSHIFT;
        linuxToWindowsKeyMap[KEY_RIGHTSHIFT] = VK_RSHIFT;
        linuxToWindowsKeyMap[KEY_LEFTALT] = VK_LMENU;
        linuxToWindowsKeyMap[KEY_RIGHTALT] = VK_RMENU;
        linuxToWindowsKeyMap[KEY_LEFTMETA] = VK_LWIN;
        linuxToWindowsKeyMap[KEY_RIGHTMETA] = VK_RWIN;
        linuxToWindowsKeyMap[KEY_BACKSLASH] = VK_OEM_5;
        linuxToWindowsKeyMap[KEY_Z] = VK_Z;
        linuxToWindowsKeyMap[KEY_X] = VK_X;
        linuxToWindowsKeyMap[KEY_C] = VK_C;
        linuxToWindowsKeyMap[KEY_V] = VK_V;
        linuxToWindowsKeyMap[KEY_B] = VK_B;
        linuxToWindowsKeyMap[KEY_N] = VK_N;
        linuxToWindowsKeyMap[KEY_M] = VK_M;
        linuxToWindowsKeyMap[KEY_COMMA] = VK_OEM_COMMA;
        linuxToWindowsKeyMap[KEY_DOT] = VK_OEM_PERIOD;
        linuxToWindowsKeyMap[KEY_SLASH] = VK_OEM_2;
        linuxToWindowsKeyMap[KEY_KPASTERISK] = VK_MULTIPLY;
        linuxToWindowsKeyMap[KEY_SPACE] = VK_SPACE;
        linuxToWindowsKeyMap[KEY_CAPSLOCK] = VK_CAPITAL;
        linuxToWindowsKeyMap[KEY_F1] = VK_F1;
        linuxToWindowsKeyMap[KEY_F2] = VK_F2;
        linuxToWindowsKeyMap[KEY_F3] = VK_F3;
        linuxToWindowsKeyMap[KEY_F4] = VK_F4;
        linuxToWindowsKeyMap[KEY_F5] = VK_F5;
        linuxToWindowsKeyMap[KEY_F6] = VK_F6;
        linuxToWindowsKeyMap[KEY_F7] = VK_F7;
        linuxToWindowsKeyMap[KEY_F8] = VK_F8;
        linuxToWindowsKeyMap[KEY_F9] = VK_F9;
        linuxToWindowsKeyMap[KEY_F10] = VK_F10;
        linuxToWindowsKeyMap[KEY_F11] = VK_F11;
        linuxToWindowsKeyMap[KEY_F12] = VK_F12;
        linuxToWindowsKeyMap[KEY_F13] = VK_F13;
        linuxToWindowsKeyMap[KEY_F14] = VK_F14;
        linuxToWindowsKeyMap[KEY_F15] = VK_F15;
        linuxToWindowsKeyMap[KEY_F16] = VK_F16;
        linuxToWindowsKeyMap[KEY_F17] = VK_F17;
        linuxToWindowsKeyMap[KEY_F18] = VK_F18;
        linuxToWindowsKeyMap[KEY_F19] = VK_F19;
        linuxToWindowsKeyMap[KEY_F20] = VK_F20;
        linuxToWindowsKeyMap[KEY_F21] = VK_F21;
        linuxToWindowsKeyMap[KEY_F22] = VK_F22;
        linuxToWindowsKeyMap[KEY_F23] = VK_F23;
        linuxToWindowsKeyMap[KEY_F24] = VK_F24;
        linuxToWindowsKeyMap[KEY_SYSRQ] = VK_PRINT;
        linuxToWindowsKeyMap[KEY_SCROLLLOCK] = VK_SCROLL;
        linuxToWindowsKeyMap[KEY_PAUSE] = VK_PAUSE;
        linuxToWindowsKeyMap[KEY_INSERT] = VK_INSERT;
        linuxToWindowsKeyMap[KEY_HOME] = VK_HOME;
        linuxToWindowsKeyMap[KEY_PAGEUP] = VK_PRIOR;
        linuxToWindowsKeyMap[KEY_DELETE] = VK_DELETE;
        linuxToWindowsKeyMap[KEY_END] = VK_END;
        linuxToWindowsKeyMap[KEY_PAGEDOWN] = VK_NEXT;
        linuxToWindowsKeyMap[KEY_RIGHT] = VK_RIGHT;
        linuxToWindowsKeyMap[KEY_LEFT] = VK_LEFT;
        linuxToWindowsKeyMap[KEY_DOWN] = VK_DOWN;
        linuxToWindowsKeyMap[KEY_UP] = VK_UP;
        linuxToWindowsKeyMap[KEY_NUMLOCK] = VK_NUMLOCK;
        linuxToWindowsKeyMap[KEY_KP7] = VK_NUMPAD7;
        linuxToWindowsKeyMap[KEY_KP8] = VK_NUMPAD8;
        linuxToWindowsKeyMap[KEY_KP9] = VK_NUMPAD9;
        linuxToWindowsKeyMap[KEY_KPMINUS] = VK_SUBTRACT;
        linuxToWindowsKeyMap[KEY_KP4] = VK_NUMPAD4;
        linuxToWindowsKeyMap[KEY_KP5] = VK_NUMPAD5;
        linuxToWindowsKeyMap[KEY_KP6] = VK_NUMPAD6;
        linuxToWindowsKeyMap[KEY_KPPLUS] = VK_ADD;
        linuxToWindowsKeyMap[KEY_KP1] = VK_NUMPAD1;
        linuxToWindowsKeyMap[KEY_KP2] = VK_NUMPAD2;
        linuxToWindowsKeyMap[KEY_KP3] = VK_NUMPAD3;
        linuxToWindowsKeyMap[KEY_KP0] = VK_NUMPAD0;
        linuxToWindowsKeyMap[KEY_KPDOT] = VK_DECIMAL;
        linuxToWindowsKeyMap[KEY_102ND] = VK_OEM_102;
        linuxToWindowsKeyMap[KEY_COMPOSE] = VK_PROCESSKEY;
    }

    public static int getWindowsKeyCode(int linuxKeyCode) {
        if (linuxKeyCode >= 0 && linuxKeyCode < KEY_CNT) {
            return linuxToWindowsKeyMap[linuxKeyCode];
        }
        return -1; // Return -1 for out-of-range or unmapped keys
    }

    public static void setKeyMapping(int linuxKeyCode, int windowsKeyCode) {
        if (linuxKeyCode >= 0 && linuxKeyCode < KEY_CNT) {
            linuxToWindowsKeyMap[linuxKeyCode] = windowsKeyCode;
        }
    }
}
