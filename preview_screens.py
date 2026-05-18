"""
Renders alliance/territory-anchor screen layouts to PNG files.
Uses the exact same coordinate values as the Java source so spatial
differences between this output and the mockups pinpoint bugs.

Each code pixel = 2 image pixels (SCALE=2) to match 2x GUI scale.
Run:  python preview_screens.py
"""

from PIL import Image, ImageDraw, ImageFont
import os, math

SCALE = 2
OUT   = "preview_out"
os.makedirs(OUT, exist_ok=True)

# ──────────────────────────────────────────────────────────────────────────────
# Approximate Minecraft default font widths (1 GUI px per char including spacing)
# ──────────────────────────────────────────────────────────────────────────────
MC_CHAR_W = {
    ' ': 4,
    'A':6,'B':6,'C':6,'D':6,'E':6,'F':6,'G':6,'H':6,'I':4,'J':6,'K':6,'L':6,
    'M':6,'N':6,'O':6,'P':6,'Q':6,'R':6,'S':6,'T':6,'U':6,'V':6,'W':6,'X':6,
    'Y':6,'Z':6,
    'a':6,'b':6,'c':6,'d':6,'e':6,'f':5,'g':6,'h':6,'i':3,'j':4,'k':5,'l':3,
    'm':6,'n':6,'o':6,'p':6,'q':6,'r':6,'s':6,'t':5,'u':6,'v':6,'w':6,'x':6,
    'y':6,'z':6,
    '0':6,'1':6,'2':6,'3':6,'4':6,'5':6,'6':6,'7':6,'8':6,'9':6,
    ':':2,'.':2,',':2,'-':6,'+':6,'!':2,'?':6,'/':6,'\\':6,'\'':4,'"':5,
    '(': 5, ')': 5, '<': 6, '>': 6, '*': 6, '@': 6, '#': 6,
}

def mc_width(text):
    return sum(MC_CHAR_W.get(c, 6) for c in text)

def argb(c):
    a = (c >> 24) & 0xFF
    r = (c >> 16) & 0xFF
    g = (c >>  8) & 0xFF
    b =  c        & 0xFF
    return (r, g, b, a)

FONT_PATH = "C:/Windows/Fonts/courbd.ttf"  # Courier Bold as MC-font stand-in
try:
    _mc_font = ImageFont.truetype(FONT_PATH, SCALE * 8)
except Exception:
    _mc_font = ImageFont.load_default()

# ──────────────────────────────────────────────────────────────────────────────
# Renderer helper
# ──────────────────────────────────────────────────────────────────────────────
class Renderer:
    def __init__(self, w, h, bg=0xFFC6C6C6):
        self.img  = Image.new("RGBA", (w * SCALE, h * SCALE), argb(bg))
        self._draw = ImageDraw.Draw(self.img)

    def _s(self, n): return n * SCALE

    def fill(self, x1, y1, x2, y2, color):
        c = argb(color)
        if c[3] == 0: return
        overlay = Image.new("RGBA", self.img.size, (0,0,0,0))
        od = ImageDraw.Draw(overlay)
        od.rectangle([self._s(x1), self._s(y1), self._s(x2)-1, self._s(y2)-1], fill=c)
        self.img = Image.alpha_composite(self.img, overlay)
        self._draw = ImageDraw.Draw(self.img)

    def text(self, txt, x, y, color=0xFF404040, italic=False):
        c = argb(color)
        if c[3] == 0: return
        self._draw.text((self._s(x), self._s(y)), txt, fill=c[:3], font=_mc_font)

    def button(self, x, y, w, h, label):
        """Vanilla-style button: grey face, dark border."""
        self.fill(x, y, x+w, y+h, 0xFFBBBBBB)
        self.fill(x, y, x+w, y+1, 0xFF555555)
        self.fill(x, y+h-1, x+w, y+h, 0xFFFFFFFF)
        self.fill(x, y, x+1, y+h, 0xFF555555)
        self.fill(x+w-1, y, x+w, y+h, 0xFFFFFFFF)
        # Centred label
        lw = mc_width(label) * SCALE
        self._draw.text((self._s(x) + (self._s(w) - lw)//2,
                         self._s(y) + (self._s(h) - SCALE*9)//2),
                        label, fill=(64,64,64), font=_mc_font)

    def slot(self, x, y):
        """18×18 inventory slot."""
        self.fill(x, y, x+18, y+18, 0xFF8B8B8B)
        self.fill(x, y, x+18, y+1,  0xFF373737)
        self.fill(x, y+17, x+18, y+18, 0xFFFFFFFF)
        self.fill(x, y, x+1,  y+18, 0xFF373737)
        self.fill(x+17, y, x+18, y+18, 0xFFFFFFFF)

    def slot_box(self, x, y, w, h):
        self.fill(x, y, x+w, y+h, 0xFF8E8E8E)
        self.fill(x, y, x+w, y+1, 0xFF373737)
        self.fill(x, y+h-1, x+w, y+h, 0xFFFFFFFF)
        self.fill(x, y, x+1, y+h, 0xFF373737)
        self.fill(x+w-1, y, x+w, y+h, 0xFFFFFFFF)

    def panel_border(self, x, y, w, h):
        self.fill(x, y, x+w, y+1, 0xFF555555)
        self.fill(x, y+h-1, x+w, y+h, 0xFFFFFFFF)
        self.fill(x, y, x+1, y+h, 0xFF555555)
        self.fill(x+w-1, y, x+w, y+h, 0xFFFFFFFF)

    def face(self, x, y, size=16):
        """Placeholder player face."""
        self.fill(x, y, x+size, y+size, 0xFFBB9977)
        self.fill(x+size//4, y+size//3, x+3*size//4, y+2*size//3, 0xFF884422)

    def scrollbar(self, x, y, h, scroll, total_items, visible_rows):
        SCROLL_TRACK  = 0xFF3A2A20
        SCROLL_HANDLE = 0xFF7A6A60
        SCROLLBAR_W   = 4
        max_s = max(0, total_items - visible_rows)
        if max_s == 0: return
        self.fill(x, y, x+SCROLLBAR_W, y+h, SCROLL_TRACK)
        thumbH = max(16, h * visible_rows // (visible_rows + max_s))
        thumbY = y + (h - thumbH) * scroll // max_s
        self.fill(x, thumbY, x+SCROLLBAR_W, thumbY+thumbH, SCROLL_HANDLE)

    def list_row(self, x, y, w, h, idx, hovered=False):
        ROW_A     = 0xFFB49268
        ROW_B     = 0xFFC088B0
        ROW_HOVER = 0xFFCFB090
        color = ROW_HOVER if hovered else (ROW_A if idx % 2 == 0 else ROW_B)
        self.fill(x, y, x+w, y+h, color)

    def save(self, name):
        path = os.path.join(OUT, name + ".png")
        self.img.save(path)
        print(f"  Saved {path}")
        return path

# ──────────────────────────────────────────────────────────────────────────────
# Shared style constants (from AllianceCreateScreen)
# ──────────────────────────────────────────────────────────────────────────────
TEXT_DARK    = 0xFF404040
TEXT_LIGHT   = 0xFFE8E8E8
INPUT_BG     = 0xFF2A1E18
INPUT_BORDER = 0xFF7A6A60
LIST_BG      = 0xFF5C4033

PANEL_BORDER_DARK  = 0xFF555555
PANEL_BORDER_LIGHT = 0xFFFFFFFF
PANEL_BG           = 0xFFC6C6C6

# ──────────────────────────────────────────────────────────────────────────────
# Helper: draw a generic alliance-screen panel frame
# ──────────────────────────────────────────────────────────────────────────────
def draw_panel(r, px, py, pw, ph):
    r.fill(px, py, px+pw, py+ph, PANEL_BG)
    r.fill(px, py, px+pw, py+1, PANEL_BORDER_DARK)
    r.fill(px, py+ph-1, px+pw, py+ph, PANEL_BORDER_LIGHT)
    r.fill(px, py, px+1, py+ph, PANEL_BORDER_DARK)
    r.fill(px+pw-1, py, px+pw, py+ph, PANEL_BORDER_LIGHT)

# ──────────────────────────────────────────────────────────────────────────────
# 1. Territory Anchor screens (282×222 GUI px)
# ──────────────────────────────────────────────────────────────────────────────
IMG_W, IMG_H = 282, 222
MAP_X, MAP_Y, MAP_SZ = 140, 7, 128
BTN_X, BTN_Y0, BTN_W, BTN_H, BTN_GAP = 9, 38, 120, 18, 4
LPX, LPY = 7, 7   # left-panel text origin
INV_X, INV_Y, HOT_Y = 8, 134, 196
BANNER_X, BANNER_Y = 207, 157

def render_territory_anchor(in_alliance, founded, name="Name", influence=0):
    r = Renderer(IMG_W, IMG_H)

    # Container outer border
    r.fill(0, 0, IMG_W, 1, PANEL_BORDER_DARK)
    r.fill(0, IMG_H-1, IMG_W, IMG_H, PANEL_BORDER_LIGHT)
    r.fill(0, 0, 1, IMG_H, PANEL_BORDER_DARK)
    r.fill(IMG_W-1, 0, IMG_W, IMG_H, PANEL_BORDER_LIGHT)

    # Map area frame
    r.fill(MAP_X-1, MAP_Y-1, MAP_X+MAP_SZ+1, MAP_Y, PANEL_BORDER_DARK)
    r.fill(MAP_X-1, MAP_Y+MAP_SZ, MAP_X+MAP_SZ+1, MAP_Y+MAP_SZ+1, PANEL_BORDER_LIGHT)
    r.fill(MAP_X-1, MAP_Y-1, MAP_X, MAP_Y+MAP_SZ+1, PANEL_BORDER_DARK)
    r.fill(MAP_X+MAP_SZ, MAP_Y-1, MAP_X+MAP_SZ+1, MAP_Y+MAP_SZ+1, PANEL_BORDER_LIGHT)
    # map placeholder
    r.fill(MAP_X, MAP_Y, MAP_X+MAP_SZ, MAP_Y+MAP_SZ, 0xFF226633)

    # Inventory slots
    for row in range(3):
        for col in range(9):
            r.slot(INV_X + col*18, INV_Y + row*18)
    for col in range(9):
        r.slot(INV_X + col*18, HOT_Y)

    # Banner slot
    r.slot(BANNER_X, BANNER_Y)

    # Left-panel text and buttons
    ly = BTN_Y0
    if in_alliance:
        r.text(f"Alliance: {name}", LPX+4, LPY+4, TEXT_DARK)
        r.text(f"Influence: {influence} inf", LPX+4, LPY+14, TEXT_DARK)
        r.text("Banner", 204, 147, TEXT_DARK)

        r.button(BTN_X, ly, BTN_W, BTN_H, "View Alliance");   ly += BTN_H + BTN_GAP
        r.button(BTN_X, ly, BTN_W, BTN_H, "Manage Invites");  ly += BTN_H + BTN_GAP
        if not founded:
            r.button(BTN_X, ly, BTN_W, BTN_H, "Found Territory")
        ly += BTN_H + BTN_GAP
        r.button(BTN_X, ly, BTN_W, BTN_H, "Leave Alliance")
    else:
        r.button(BTN_X, ly, BTN_W, BTN_H, "Create Alliance")

    # Inventory label
    r.text("Inventory", INV_X, INV_Y - 11, TEXT_DARK)

    return r

print("Rendering Territory Anchor screens…")
render_territory_anchor(False, False).save("1_anchor_no_alliance")
render_territory_anchor(True,  True ).save("2_anchor_alliance_founded")
render_territory_anchor(True,  False).save("3_anchor_alliance_unfounded")

# ──────────────────────────────────────────────────────────────────────────────
# Alliance screen window: assume 858×483 game window, GUI scale 1
# panelLeft = (858-PANEL_W)//2,  panelTop = (483-PANEL_H)//2
# ──────────────────────────────────────────────────────────────────────────────
WIN_W, WIN_H = 858, 483

def new_alliance_screen(pw, ph):
    r = Renderer(WIN_W, WIN_H, 0xAA000000)
    px = (WIN_W - pw) // 2
    py = (WIN_H - ph) // 2
    draw_panel(r, px, py, pw, ph)
    return r, px, py

# ──────────────────────────────────────────────────────────────────────────────
# 2. Create Alliance (PANEL_W=274, PANEL_H=196)
# ──────────────────────────────────────────────────────────────────────────────
PANEL_W_CREATE, PANEL_H_CREATE = 274, 196
SCROLLBAR_W = 4
ROW_H = 13
FACE_SIZE = 10

def render_create_alliance(members=None):
    if members is None:
        members = [("cnn_r", True), ("jrmiah", False), ("cnn_r2", False)]
    pw, ph = PANEL_W_CREATE, PANEL_H_CREATE
    r, px, py = new_alliance_screen(pw, ph)

    # Back button
    r.button(px+5, py+5, 9, 9, "<")
    # Alliance Name label
    r.text("Alliance Name", px+19, py+5, TEXT_DARK)
    # EditBox
    r.fill(px+18, py+15, px+pw-4, py+27, INPUT_BORDER)
    r.fill(px+19, py+16, px+pw-5, py+26, INPUT_BG)
    r.text("_", px+21, py+17, 0xFF888888)

    # Invite Members label
    r.text("Invite Members", px+5, py+29, TEXT_DARK)

    # List
    lx = px+5
    ly = py+40
    lw = pw - 10 - SCROLLBAR_W - 2
    lh = 130
    r.fill(lx, ly, lx+lw, ly+lh, LIST_BG)

    visible = lh // ROW_H
    for i, (name, selected) in enumerate(members[:visible]):
        ry = ly + i * ROW_H
        r.list_row(lx, ry, lw, ROW_H, i, hovered=(i==1))
        r.face(lx+3, ry+(ROW_H-FACE_SIZE)//2, FACE_SIZE)
        r.text(name, lx+3+FACE_SIZE+4, ry+(ROW_H-9)//2, TEXT_DARK)
        mark = "X" if selected else "o"
        mw = mc_width(mark)
        r.text(mark, lx+lw-mw-4, ry+(ROW_H-9)//2, 0xFF44AA44 if selected else 0xFF888888)

    # Scrollbar
    r.scrollbar(lx+lw+2, ly, lh, 0, len(members), visible)

    # Create / Cancel buttons
    bw = (pw - 14) // 2
    r.button(px+5, py+174, bw, 12, "Create")
    r.button(px+5+bw+4, py+174, bw, 12, "Cancel")

    return r

print("Rendering Create Alliance…")
render_create_alliance().save("4_create_alliance")

# ──────────────────────────────────────────────────────────────────────────────
# 3. View Alliance  (PANEL_W=274, PANEL_H=221)
# ──────────────────────────────────────────────────────────────────────────────
PANEL_W_VIEW, PANEL_H_VIEW = 274, 221

def render_view_alliance(editing_name=False,
                         members=None, influence=0, name="Name"):
    if members is None:
        members = [("cnn_r", "Founder", True), ("jrmiah", "Member", False)]
    pw, ph = PANEL_W_VIEW, PANEL_H_VIEW
    r, px, py = new_alliance_screen(pw, ph)

    lx = px+5
    lw = pw - 10 - SCROLLBAR_W - 2
    ly = py+38
    lh = ph - 38 - 8
    sx = lx + lw + 2

    # Influence (always shown, right-aligned)
    inf_text = f"Influence: {influence} inf"
    infw = mc_width(inf_text)
    r.text(inf_text, px+pw-infw-5, py+5, TEXT_DARK)

    if editing_name:
        # Back < button
        r.button(px+5, py+5, 9, 9, "<")
        # Input spans from px+18 to just left of influence text
        confirm_x = px + pw - infw - 5 - 2 - 22
        input_w = confirm_x - (px+18) - 2
        r.fill(px+17, py+4, px+18+input_w+2, py+14, INPUT_BORDER)
        r.fill(px+18, py+5, px+18+input_w, py+14, INPUT_BG)
        r.text("_", px+20, py+6, 0xFF888888)
        r.button(confirm_x, py+5, 22, 9, "v")
    else:
        r.button(px+5, py+5, 9, 9, "<")
        r.text(f"Alliance: {name}", px+18, py+5, TEXT_DARK)
        # Edit Name button (owner only)
        r.button(px+5, py+16, 48, 9, "Edit Name")

    # Members: label
    r.text("Members:", px+5, py+27, TEXT_DARK)

    # List background
    r.fill(lx, ly, lx+lw, ly+lh, LIST_BG)

    visible = lh // ROW_H
    for i, (mname, role, is_owner) in enumerate(members[:visible]):
        ry = ly + i * ROW_H
        r.list_row(lx, ry, lw, ROW_H, i, hovered=(i==1))
        r.face(lx+3, ry+(ROW_H-FACE_SIZE)//2, FACE_SIZE)
        r.text(mname, lx+3+FACE_SIZE+4, ry+(ROW_H-9)//2, TEXT_DARK)
        rw = mc_width(role)
        role_x = lx+lw-rw-4
        if not is_owner: role_x -= 14
        r.text(role, role_x, ry+(ROW_H-9)//2, TEXT_DARK)
        if not is_owner:
            r.text("e", lx+lw-12, ry+(ROW_H-9)//2, 0xFF446699)  # pencil stand-in

    # Scrollbar
    r.scrollbar(sx, ly, lh, 0, len(members), visible)

    return r

print("Rendering View Alliance…")
render_view_alliance(editing_name=False).save("5_view_alliance_normal")
render_view_alliance(editing_name=True).save("6_view_alliance_edit_name")

# ──────────────────────────────────────────────────────────────────────────────
# 4. Edit Member  (PANEL_W=274, PANEL_H=227)
# ──────────────────────────────────────────────────────────────────────────────
PANEL_W_EDIT, PANEL_H_EDIT = 274, 227
FACE_SIZE_LARGE = 29
PERM_ROW_H = 13

PERMS = [("Build", True), ("Break Blocks", False), ("PvP", False), ("PvE", False)]

def render_edit_member(mode="normal"):
    pw, ph = PANEL_W_EDIT, PANEL_H_EDIT
    r, px, py = new_alliance_screen(pw, ph)

    plx = px+5
    ply = py+125
    plw = pw - 10 - SCROLLBAR_W - 2
    plh = ph - 125 - 8
    psx = plx + plw + 2

    # Back button
    r.button(px+5, py+5, 9, 9, "<")

    # Large face portrait
    r.face(px+5, py+18, FACE_SIZE_LARGE)

    if mode == "normal":
        r.text("Name: jrmiah", px+42, py+19, TEXT_DARK)
        r.text("Role: Member", px+42, py+26, TEXT_DARK)
        r.button(px+42, py+36, 48, 9, "Edit Role")
        r.button(px+94, py+36, 36, 9, "Kick")

    elif mode == "edit_role":
        r.text("Name: jrmiah", px+42, py+19, TEXT_DARK)
        r.text("Role:", px+42, py+26, TEXT_DARK)
        r.fill(px+59, py+24, px+210, py+34, INPUT_BG)
        r.text("_", px+61, py+25, 0xFF888888)
        r.button(px+212, py+25, 8, 8, "v")

    elif mode == "kick":
        r.text("Are you sure you want to kick member?", px+42, py+18, TEXT_DARK)
        r.button(px+42, py+36, 36, 9, "Yes")
        r.button(px+82, py+36, 24, 9, "No")

    # Permissions section
    r.text("Member Permissions", px+5, py+116, TEXT_DARK)
    r.fill(plx, ply, plx+plw, ply+plh, LIST_BG)

    visible = plh // PERM_ROW_H
    for i, (pname, enabled) in enumerate(PERMS[:visible]):
        ry = ply + i * PERM_ROW_H
        r.list_row(plx, ry, plw, PERM_ROW_H, i, hovered=(i==1))
        color = 0xFF44AA44 if enabled else 0xFFAA4444
        r.text(pname, plx+4, ry+(PERM_ROW_H-9)//2, color)
        check = "X" if enabled else "o"
        cw = mc_width(check)
        r.text(check, plx+plw-cw-4, ry+(PERM_ROW_H-9)//2, color)

    r.scrollbar(psx, ply, plh, 0, len(PERMS), visible)

    return r

print("Rendering Edit Member…")
render_edit_member("normal").save("7_edit_member_normal")
render_edit_member("edit_role").save("8_edit_member_edit_role")
render_edit_member("kick").save("9_edit_member_kick")

# ──────────────────────────────────────────────────────────────────────────────
# 5. Manage Invites  (PANEL_W=274, PANEL_H=221)
# ──────────────────────────────────────────────────────────────────────────────
PANEL_W_INVITE, PANEL_H_INVITE = 274, 221

def render_manage_invites(members=None, name="Name"):
    if members is None:
        members = [("cnn_r", True), ("jrmiah", False), ("cnn_r2", False)]
    pw, ph = PANEL_W_INVITE, PANEL_H_INVITE
    r, px, py = new_alliance_screen(pw, ph)

    lx = px+5
    ly = py+61
    lw = pw - 10 - SCROLLBAR_W - 2
    lh = ph - 61 - 20 - 4
    sx = lx + lw + 2

    # Back button
    r.button(px+5, py+5, 9, 9, "<")
    r.text(f"Alliance: {name}", px+18, py+5, TEXT_DARK)

    # Join Letter (centered in panel)
    r.text("Join Letter", px+104, py+21, TEXT_DARK)
    r.slot_box(px+110, py+32, 18, 18)
    r.text("->", px+131, py+37, TEXT_DARK)
    r.slot_box(px+146, py+32, 18, 18)

    # Invite Members label
    r.text("Invite Members", px+5, py+55, TEXT_DARK)

    # List
    r.fill(lx, ly, lx+lw, ly+lh, LIST_BG)
    visible = lh // ROW_H
    for i, (mname, selected) in enumerate(members[:visible]):
        ry = ly + i * ROW_H
        r.list_row(lx, ry, lw, ROW_H, i, hovered=(i==1))
        r.face(lx+3, ry+(ROW_H-FACE_SIZE)//2, FACE_SIZE)
        r.text(mname, lx+3+FACE_SIZE+4, ry+(ROW_H-9)//2, TEXT_DARK)
        mark = "X" if selected else "o"
        mw = mc_width(mark)
        r.text(mark, lx+lw-mw-4, ry+(ROW_H-9)//2, 0xFF44AA44 if selected else 0xFF888888)

    r.scrollbar(sx, ly, lh, 0, len(members), visible)

    # Buttons
    bw = (pw - 14) // 2
    bY = py + ph - 17
    r.button(px+5, bY, bw, 12, "Invite")
    r.button(px+5+bw+4, bY, bw, 12, "Clear")

    return r

print("Rendering Manage Invites…")
render_manage_invites().save("10_manage_invites")

print(f"\nAll previews saved to ./{OUT}/")
