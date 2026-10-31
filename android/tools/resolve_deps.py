#!/usr/bin/env python3
"""
Resolvedor mínimo de dependencias Maven para el build manual de Admin 3FORCE.

Lee los POM reales de Google Maven / Maven Central (vía curl, que sí tiene
salida a red en esta VM), resuelve el grafo transitivo (scopes compile/runtime,
con dependencyManagement y BOMs) eligiendo la versión más alta en conflictos,
y descarga los .aar/.jar resultantes a ../deps/ junto a un manifiesto de texto.

Uso: python3 resolve_deps.py group:artifact:version [mas raíces...]
"""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
DEPS_DIR = os.path.join(HERE, "..", "deps")
CACHE_DIR = os.path.join(HERE, "..", "build", "pom-cache")

REPOS = [
    "https://dl.google.com/dl/android/maven2",
    "https://repo.maven.apache.org/maven2",
]

NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def valid_pom(path):
    try:
        with open(path, "rb") as f:
            return b"<project" in f.read(2048)
    except OSError:
        return False


def valid_zip(path):
    try:
        with open(path, "rb") as f:
            return f.read(2) == b"PK"
    except OSError:
        return False


def fetch(url: str, dest: str, validator) -> bool:
    if os.path.exists(dest) and os.path.getsize(dest) > 0 and validator(dest):
        return True
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    r = subprocess.run(
        ["curl", "-sL", "--retry", "3", "-o", dest, "-w", "%{http_code}", url],
        capture_output=True, text=True,
    )
    ok = (
        r.stdout.strip() == "200"
        and os.path.exists(dest)
        and os.path.getsize(dest) > 0
        and validator(dest)
    )
    if not ok and os.path.exists(dest):
        os.remove(dest)
    return ok


def pom_path(g, a, v):
    return os.path.join(CACHE_DIR, g.replace(".", "/"), a, v, f"{a}-{v}.pom")


def get_pom(g, a, v):
    dest = pom_path(g, a, v)
    if os.path.exists(dest) and valid_pom(dest):
        return dest
    for repo in REPOS:
        url = f"{repo}/{g.replace('.', '/')}/{a}/{v}/{a}-{v}.pom"
        if fetch(url, dest, valid_pom):
            return dest
    return None


def strip(tag):
    return tag.split("}", 1)[-1]


def children(el, name):
    return [c for c in el if strip(c.tag) == name]


def child(el, name):
    for c in el:
        if strip(c.tag) == name:
            return c
    return None


def text(el, name, default=None):
    c = child(el, name)
    if c is not None and c.text:
        return c.text.strip()
    return default


def parse_version(v):
    # Quita rangos duros tipo [1.6.0] o (1.5,1.7]
    v = v.strip().strip("[]()")
    v = v.split(",")[-1].strip()
    return v


def ver_key(v):
    parts = re.split(r"[.\-]", v)
    key = []
    for p in parts:
        key.append((0, int(p), "") if p.isdigit() else (1, 0, p))
    return key


class Pom:
    def __init__(self, g, a, v):
        self.g, self.a, self.v = g, a, v
        self.packaging = "jar"
        self.properties = {}
        self.dep_mgmt = {}   # (g,a) -> version
        self.deps = []       # (g,a,version,scope,type)
        path = get_pom(g, a, v)
        if path is None:
            raise RuntimeError(f"POM no encontrado: {g}:{a}:{v}")
        root = ET.parse(path).getroot()
        self.packaging = text(root, "packaging", "jar")
        # Propiedades propias
        props_el = child(root, "properties")
        if props_el is not None:
            for p in props_el:
                self.properties[strip(p.tag)] = (p.text or "").strip()
        self.properties.setdefault("project.version", v)
        self.properties.setdefault("pom.version", v)
        # Padre: hereda propiedades y dependencyManagement
        parent_el = child(root, "parent")
        self.parent = None
        if parent_el is not None:
            pg, pa, pv = text(parent_el, "groupId"), text(parent_el, "artifactId"), text(parent_el, "version")
            if pg and pa and pv:
                try:
                    self.parent = Pom(pg, pa, pv)
                    for k, val in self.parent.properties.items():
                        self.properties.setdefault(k, val)
                    self.dep_mgmt.update(self.parent.dep_mgmt)
                except RuntimeError:
                    pass
        # dependencyManagement propio (puede importar BOMs)
        dm_el = child(root, "dependencyManagement")
        if dm_el is not None:
            deps_el = child(dm_el, "dependencies")
            if deps_el is not None:
                for d in children(deps_el, "dependency"):
                    self._read_dep(d, into_mgmt=True)
        # Dependencias propias
        deps_el = child(root, "dependencies")
        if deps_el is not None:
            for d in children(deps_el, "dependency"):
                self._read_dep(d, into_mgmt=False)

    def resolve_str(self, s):
        if s is None:
            return None
        def repl(m):
            return self.properties.get(m.group(1), m.group(0))
        prev = None
        while prev != s:
            prev = s
            s = re.sub(r"\$\{([^}]+)\}", repl, s)
        return s

    def _read_dep(self, d, into_mgmt):
        g = self.resolve_str(text(d, "groupId"))
        a = self.resolve_str(text(d, "artifactId"))
        v = self.resolve_str(text(d, "version"))
        scope = text(d, "scope", "compile")
        typ = text(d, "type", "jar")
        optional = text(d, "optional", "false") == "true"
        if not g or not a:
            return
        if scope == "import" and typ == "pom":
            # BOM: importa su dependencyManagement
            try:
                bom = Pom(g, a, parse_version(v))
                self.dep_mgmt.update(bom.dep_mgmt)
            except RuntimeError:
                pass
            return
        if into_mgmt:
            if v:
                self.dep_mgmt[(g, a)] = parse_version(v)
            return
        if scope in ("test", "provided", "system") or optional:
            return
        if not v:
            v = self.dep_mgmt.get((g, a))
        if not v:
            return
        self.deps.append((g, a, parse_version(v), scope, typ))


def resolve(roots):
    chosen = {}  # (g,a) -> version
    order = []
    queue = list(roots)
    while queue:
        g, a, v = queue.pop(0)
        key = (g, a)
        if key in chosen and ver_key(chosen[key]) >= ver_key(v):
            continue
        chosen[key] = v
        if key not in order:
            order.append(key)
        pom = Pom(g, a, v)
        for dg, da, dv, scope, typ in pom.deps:
            dv = pom.dep_mgmt.get((dg, da), dv)
            queue.append((dg, da, dv))
    return [(g, a, chosen[(g, a)]) for (g, a) in order]


def jar_has_classes(path):
    try:
        import zipfile

        with zipfile.ZipFile(path) as z:
            return any(n.endswith(".class") for n in z.namelist())
    except Exception:
        return False


def main():
    roots = []
    for spec in sys.argv[1:]:
        g, a, v = spec.split(":")
        roots.append((g, a, v))
    os.makedirs(DEPS_DIR, exist_ok=True)
    resolved = resolve(roots)
    manifest_lines = []
    for g, a, v in resolved:
        pom = Pom(g, a, v)
        ext = "aar" if pom.packaging == "aar" else "jar"
        fname = f"{a}-{v}.{ext}"
        dest = os.path.join(DEPS_DIR, fname)
        ok = False
        for repo in REPOS:
            url = f"{repo}/{g.replace('.', '/')}/{a}/{v}/{fname}"
            if fetch(url, dest, valid_zip):
                ok = True
                break
        # Artefactos KMP raíz (p. ej. androidx.annotation:annotation): el jar
        # viene vacío de clases; las clases reales están en <artifact>-jvm.
        if ok and ext == "jar" and not jar_has_classes(dest):
            alt_a = f"{a}-jvm"
            alt_fname = f"{alt_a}-{v}.jar"
            alt_dest = os.path.join(DEPS_DIR, alt_fname)
            for repo in REPOS:
                url = f"{repo}/{g.replace('.', '/')}/{alt_a}/{v}/{alt_fname}"
                if fetch(url, alt_dest, valid_zip) and jar_has_classes(alt_dest):
                    os.remove(dest)
                    a, fname, dest = alt_a, alt_fname, alt_dest
                    break
        status = "OK " if ok else "FALLO"
        print(f"{status} {g}:{a}:{v} ({ext}) -> {fname}")
        manifest_lines.append(f"{g}:{a}:{v}:{ext}")
    with open(os.path.join(DEPS_DIR, "deps.txt"), "w") as f:
        f.write("\n".join(manifest_lines) + "\n")
    print(f"\nTotal: {len(resolved)} artefactos en {os.path.abspath(DEPS_DIR)}")


if __name__ == "__main__":
    main()
