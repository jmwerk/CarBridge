"""Add the Android Auto entries from AABrowser's manifest to a decoded (apktool) manifest.

Usage: python3 -I inject_car_manifest.py <decoded>/AndroidManifest.xml [--rename NEW_PACKAGE]

--rename gives the patched copy a new package name so it installs next to the original app
instead of replacing it (no uninstall, original data untouched).
"""
import os
import sys
import xml.etree.ElementTree as ET

A = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", A)
ET.register_namespace("tools", "http://schemas.android.com/tools")

COMPONENT_TAGS = ("activity", "activity-alias", "service", "receiver", "provider", "application")


def attr(name):
    return f"{{{A}}}{name}"


def ensure_meta(parent, name, **values):
    for m in parent.findall("meta-data"):
        if m.get(attr("name")) == name:
            return False
    m = ET.SubElement(parent, "meta-data", {attr("name"): name})
    for k, v in values.items():
        m.set(attr(k), v)
    return True


def is_launcher(filter_el):
    actions = {a.get(attr("name")) for a in filter_el.findall("action")}
    cats = {c.get(attr("name")) for c in filter_el.findall("category")}
    return "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in cats


def add_car_entries(root):
    app = root.find("application")
    if app is None:
        sys.exit("no <application> element")

    app.set(attr("appCategory"), "game")
    ensure_meta(app, "distractionOptimized", value="true")
    ensure_meta(app, "androidx.car.app.minCarApiLevel", value="7")
    ensure_meta(app, "com.google.android.gms.car.application", resource="@xml/automotive_app_desc")

    # Android Auto only lists non-template apps that claim an allowed category (found in spike)
    for perm in ("androidx.car.app.ACCESS_SURFACE", "androidx.car.app.MAP_TEMPLATES"):
        if not any(p.get(attr("name")) == perm for p in root.findall("uses-permission")):
            ET.SubElement(root, "uses-permission", {attr("name"): perm})

    patched = []
    for act in app.findall("activity") + app.findall("activity-alias"):
        for f in act.findall("intent-filter"):
            if not is_launcher(f):
                continue
            cats = {c.get(attr("name")) for c in f.findall("category")}
            for cat in ("android.intent.category.CAR_LAUNCHER",
                        "androidx.car.app.category.NAVIGATION",
                        "android.intent.category.APP_MAPS"):
                if cat not in cats:
                    ET.SubElement(f, "category", {attr("name"): cat})
            ensure_meta(act, "distractionOptimized", value="true")
            act.set(attr("resizeableActivity"), "true")
            patched.append(act.get(attr("name")))

    if not patched:
        sys.exit("no MAIN/LAUNCHER activity found")
    print("car-enabled launcher activities:", ", ".join(patched))
    strip_car_template_services(app)


# Android Auto prefers these over a launcher activity, so it would show its own template UI
TEMPLATE_SERVICE_ACTIONS = {
    "android.media.browse.MediaBrowserService",
    "androidx.media3.session.MediaLibraryService",
    "androidx.car.app.CarAppService",
}


def strip_car_template_services(app):
    for svc in app.findall("service"):
        for f in svc.findall("intent-filter"):
            actions = {a.get(attr("name")) for a in f.findall("action")}
            if actions & TEMPLATE_SERVICE_ACTIONS:
                svc.remove(f)
                print("removed car template filter from", svc.get(attr("name")))


def rewrite_string_resource(res_dir, name, old, new):
    path = os.path.join(res_dir, "values", "strings.xml")
    tree = ET.parse(path)
    for s in tree.getroot().findall("string"):
        if s.get("name") == name and s.text:
            s.text = rename_value(s.text, old, new, force=True)
            tree.write(path, encoding="utf-8", xml_declaration=True)
            return
    sys.exit(f"@string/{name} not found in {path}")


def rename_value(value, old, new, force=False):
    """Swap the old package prefix; with force, prefix values that don't carry it."""
    parts = []
    for part in value.split(";"):
        if part == old or part.startswith(old + "."):
            parts.append(new + part[len(old):])
        elif force:
            parts.append(f"{new}.{part}")
        else:
            parts.append(part)
    return ";".join(parts)


def rename_package(root, manifest_path, new):
    old = root.get("package")
    if not old:
        sys.exit("manifest has no package attribute")
    root.set("package", new)

    # Relative class names resolve against the package; pin them to the original classes
    for tag in COMPONENT_TAGS:
        for el in root.iter(tag):
            for key in ("name", "targetActivity", "backupAgent"):
                v = el.get(attr(key))
                if v and v.startswith("."):
                    el.set(attr(key), old + v)

    # Authorities and declared permissions must be unique per device
    declared = set()
    for p in root.findall("permission") + root.findall("permission-tree"):
        name = p.get(attr("name"))
        declared.add(name)
        p.set(attr("name"), rename_value(name, old, new, force=True))
    for el in root.iter():
        for key in ("permission", "readPermission", "writePermission", "name"):
            v = el.get(attr(key))
            if v in declared and el.tag not in ("permission", "permission-tree"):
                el.set(attr(key), rename_value(v, old, new, force=True))

    res_dir = os.path.join(os.path.dirname(manifest_path), "res")
    for prov in root.iter("provider"):
        auth = prov.get(attr("authorities"))
        if not auth:
            continue
        if auth.startswith("@string/"):
            rewrite_string_resource(res_dir, auth[len("@string/"):], old, new)
        else:
            prov.set(attr("authorities"), rename_value(auth, old, new, force=True))
    print(f"renamed package {old} -> {new}")


def main(argv):
    if len(argv) not in (2, 4) or (len(argv) == 4 and argv[2] != "--rename"):
        sys.exit(__doc__)
    path = argv[1]
    tree = ET.parse(path)
    root = tree.getroot()
    add_car_entries(root)
    if len(argv) == 4:
        rename_package(root, path, argv[3])
    tree.write(path, encoding="utf-8", xml_declaration=True)


if __name__ == "__main__":
    main(sys.argv)
