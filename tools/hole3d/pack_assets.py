"""Packt ausgewählte Modelle des Kenney Food Kit in EINE GLB-Datei für das Spiel.

Aufruf (Blender 4/5, ohne Oberfläche):
    Blender -b -P pack_assets.py -- <Ordner "GLB format"> <Ziel.glb> <Ziel-Manifest.json>

Je Modell: alle Teile zu einem Netz verbinden, Ursprung unten in die Mitte der Standfläche
legen, auf Standfläche 1 × 1 skalieren (größte Kante in der Ebene = 1). Alle Modelle teilen
sich ein Material mit der Farbtafel des Kits. Das Manifest hält je Modell die Höhe fest.
"""
import json
import os
import sys

import bpy
from mathutils import Vector

src_dir, out_glb, out_manifest = sys.argv[sys.argv.index("--") + 1:][:3]
names = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "models.json")))["models"]

bpy.ops.wm.read_factory_settings(use_empty=True)
shared_material = None
manifest = {}

for name in names:
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=os.path.join(src_dir, name + ".glb"))
    imported = [o for o in bpy.data.objects if o not in before]
    meshes = [o for o in imported if o.type == "MESH"]
    helper_names = [o.name for o in imported if o.type != "MESH"]
    if not meshes:
        raise SystemExit(f"{name}: kein Netz gefunden")

    # Aus der Hierarchie lösen (Lage behalten), dann zu einem Objekt verbinden.
    bpy.ops.object.select_all(action="DESELECT")
    for o in meshes:
        o.select_set(True)
    bpy.context.view_layer.objects.active = meshes[0]
    bpy.ops.object.parent_clear(type="CLEAR_KEEP_TRANSFORM")
    if len(meshes) > 1:
        bpy.ops.object.join()
    obj = bpy.context.view_layer.objects.active
    # Leere Elternknoten der GLB — per Namen holen, die alten Verweise sind nach dem Verbinden ungültig.
    for helper in helper_names:
        if helper in bpy.data.objects:
            bpy.data.objects.remove(bpy.data.objects[helper], do_unlink=True)
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

    # Ein gemeinsames Material für alle Modelle (jede GLB bringt ihre eigene Kopie mit).
    if shared_material is None:
        shared_material = obj.data.materials[0]
        shared_material.name = "food"
    obj.data.materials.clear()
    obj.data.materials.append(shared_material)

    # Standfläche in die Mitte, Unterkante auf den Boden, Standfläche auf 1 normieren.
    corners = [obj.matrix_world @ Vector(c) for c in obj.bound_box]
    lo = Vector((min(c.x for c in corners), min(c.y for c in corners), min(c.z for c in corners)))
    hi = Vector((max(c.x for c in corners), max(c.y for c in corners), max(c.z for c in corners)))
    size = hi - lo
    footprint = max(size.x, size.y)
    scale = 1.0 / footprint
    for v in obj.data.vertices:
        v.co.x = (v.co.x - (lo.x + hi.x) / 2) * scale
        v.co.y = (v.co.y - (lo.y + hi.y) / 2) * scale
        v.co.z = (v.co.z - lo.z) * scale
    obj.data.update()
    obj.location = (0, 0, 0)
    obj.name = obj.data.name = name
    manifest[name] = {
        "height": round(size.z * scale, 4),
        # Schmale Seite der Standfläche im Verhältnis zur langen (1 = rund/quadratisch).
        "aspect": round(min(size.x, size.y) * scale, 4),
        "tris": sum(len(p.vertices) - 2 for p in obj.data.polygons),
    }



def colored_part(make, color, **kwargs):
    """Legt ein Grundobjekt an und färbt es über eine Farbschicht ein (kein Bild nötig)."""
    make(**kwargs)
    part = bpy.context.view_layer.objects.active
    layer = part.data.color_attributes.new(name="Color", type="BYTE_COLOR", domain="CORNER")
    for item in layer.data:
        item.color = (*color, 1.0)
    return part


def make_bomb():
    """Die Bombe gibt es im Kit nicht: Kugel, Kappe, Zündschnur und Funke aus Grundkörpern."""
    parts = [
        colored_part(bpy.ops.mesh.primitive_uv_sphere_add, (0.035, 0.035, 0.05), segments=20, ring_count=12, radius=0.5, location=(0, 0, 0.5)),
        colored_part(bpy.ops.mesh.primitive_cylinder_add, (0.28, 0.29, 0.33), vertices=12, radius=0.17, depth=0.16, location=(0, 0, 1.02)),
        colored_part(bpy.ops.mesh.primitive_cylinder_add, (0.62, 0.45, 0.25), vertices=8, radius=0.04, depth=0.3, location=(0.05, 0, 1.22), rotation=(0, 0.35, 0)),
        colored_part(bpy.ops.mesh.primitive_ico_sphere_add, (1.0, 0.42, 0.08), subdivisions=1, radius=0.11, location=(0.11, 0, 1.4)),
    ]
    bpy.ops.object.select_all(action="DESELECT")
    for part in parts:
        part.select_set(True)
    bpy.context.view_layer.objects.active = parts[0]
    bpy.ops.object.join()
    bomb = bpy.context.view_layer.objects.active
    bpy.ops.object.shade_smooth()
    bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
    material = bpy.data.materials.new("bomb")
    material.use_nodes = True
    nodes = material.node_tree.nodes
    attribute = nodes.new("ShaderNodeVertexColor")
    attribute.layer_name = "Color"
    material.node_tree.links.new(attribute.outputs["Color"], nodes["Principled BSDF"].inputs["Base Color"])
    bomb.data.materials.append(material)
    bomb.name = bomb.data.name = "bomb"
    height = max(v.co.z for v in bomb.data.vertices)
    manifest["bomb"] = {"height": round(height, 4), "aspect": 1.0, "tris": sum(len(p.vertices) - 2 for p in bomb.data.polygons)}


make_bomb()

for block in list(bpy.data.materials):
    if block.users == 0:
        bpy.data.materials.remove(block)

bpy.ops.export_scene.gltf(
    filepath=out_glb,
    export_format="GLB",
    export_apply=True,
    export_yup=True,
    export_animations=False,
    export_cameras=False,
    export_lights=False,
)
json.dump(manifest, open(out_manifest, "w"), indent=1, sort_keys=True)
print("GEPACKT", len(manifest), "Modelle,", sum(m["tris"] for m in manifest.values()), "Dreiecke")
