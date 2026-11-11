"""Rebuild original artwork/data without third-party packages.
Run: python3 tools/import_original.py
Inputs are the unmodified Neil ZIP contents under art/original.
"""
from pathlib import Path
import struct, zlib, json, hashlib
ROOT = Path(__file__).resolve().parents[1]
SRC = next((ROOT / "art/original").glob("*/MAIN_FILES"))
OUT = ROOT / "art/sprites/amiga"
OUT.mkdir(parents=True, exist_ok=True)
def png(path,w,h,pixels):
    def chunk(t,b): return struct.pack(">I",len(b))+t+b+struct.pack(">I",zlib.crc32(t+b))
    raw=b"".join(b"\0"+bytes(pixels[y*w*4:(y+1)*w*4]) for y in range(h))
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">2I5B",w,h,8,6,0,0,0))+chunk(b"IDAT",zlib.compress(raw))+chunk(b"IEND",b""))
def bank(name,transparent):
    b=(SRC/name).read_bytes(); assert b[:4] in (b"AmIc",b"AmSp")
    count=struct.unpack_from(">H",b,4)[0]; pos=6; frames=[]
    for i in range(count):
        words,h,planes,hx,hy=struct.unpack_from(">5H",b,pos); pos+=10
        size=words*2*h*planes
        frames.append((words*16,h,planes,b[pos:pos+size],hx,hy)); pos+=size
    assert len(b)-pos==64, (name,pos,len(b))
    palette=struct.unpack_from(">32H",b,pos)
    colors=[((c>>8&15)*17,(c>>4&15)*17,(c&15)*17,255) for c in palette]
    result=[]
    for w,h,planes,data,hx,hy in frames:
        px=bytearray()
        for y in range(h):
            for x in range(w):
                index=sum(((data[p*w//8*h+y*w//8+x//8]>>(7-x%8))&1)<<p for p in range(planes))
                px.extend((0,0,0,0) if transparent and index==0 else colors[index])
        result.append((w,h,px))
    return result
tiles=bank("Tiles.GRA",False); sprites=bank("SPRITES.GRA",True)
for i,(w,h,px) in enumerate(sprites,1): png(OUT/f"sprites/{i:03}.png",w,h,px)
b=(SRC/"GAME_MAP.MAP").read_bytes(); width,height=struct.unpack_from(">HH",b)
assert (width,height)==(300,120)
grid=struct.unpack(">36000H",b[4:]); values=(SRC/"GAME_MAP.VAL").read_bytes()[20:]
names=(SRC/"ROOM_NAMES.DAT").read_text("latin1").splitlines()
rooms=[]; used=[]
for r in range(100):
    ids=[grid[(r//15*12+y)*width+r%15*20+x] for y in range(12) for x in range(20)]
    if any(ids): used.append(r)
    pixels=bytearray(320*192*4)
    for j,index in enumerate(ids):
        # Map indices are zero based; bank images are numbered from one in AMOS.
        w,h,px=tiles[index&0x3fff]
        for y in range(16):
            for x in range(16):
                sx=15-x if index&0x8000 else x; sy=15-y if index&0x4000 else y
                dst=((j//20*16+y)*320+j%20*16+x)*4
                pixels[dst:dst+4]=px[(sy*w+sx)*4:(sy*w+sx)*4+4]
    png(OUT/f"rooms/room_{r:03}.png",320,192,pixels)
    rooms.append(dict(id=r,name=names[r],tiles=ids,collision=[values[t&0x3fff] for t in ids]))
def records(name,size):
    b=(SRC/name).read_bytes()[20:]
    return [list(struct.unpack(">"+str(size//2)+"h",b[i:i+size])) for i in range(0,len(b),size)]
data=dict(rooms=rooms,used=used,objects=records("OBJECTS.DAT",20),bobs=records("BOBS.DAT",30),
          objectNames=(SRC/"OBJECT_NAMES.DAT").read_text("latin1").splitlines(),
          messages=(SRC/"MESSAGES.DAT").read_text("latin1").splitlines(),
          statistics=[int(x) for x in (SRC/"STATISTICS.DAT").read_text().split()],
          zones=[int(x) for x in (SRC/"ZONES.DAT").read_text().split()])
(OUT/"world.json").write_text(json.dumps(data,separators=(",",":")))
(OUT/"provenance.json").write_text(json.dumps({p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in SRC.iterdir() if p.is_file()},indent=2))
# A compact sheet for visual verification.
sheet=bytearray(512*(((len(sprites)+7)//8)*64)*4)
for i,(w,h,px) in enumerate(sprites):
    for y in range(min(h,64)):
        for x in range(min(w,64)):
            dst=(((i//8)*64+y)*512+(i%8)*64+x)*4
            sheet[dst:dst+4]=px[(y*w+x)*4:(y*w+x)*4+4]
png(OUT/"sprite_sheet.png",512,((len(sprites)+7)//8)*64,sheet)
print(f"Decoded {len(tiles)} tiles, {len(sprites)} sprites, {len(used)} occupied rooms.")
