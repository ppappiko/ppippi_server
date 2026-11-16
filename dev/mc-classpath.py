"""Write a javac @argfile classpath for the NeoForge client mods from the local Minecraft launcher install.

Used by dev/pipi-facilities/build.sh and dev/pipi-travel/build.sh when the old compile workspace
(C:/Users/user/Documents/Codex/.../sor) is not on this PC. Needs NeoForge for 1.21.1 installed in the
launcher once (it creates .minecraft/versions/neoforge-<ver>/ and the patched client jars).

usage: python dev/mc-classpath.py <out.args> [neoforge-version]
"""
import glob
import json
import os
import sys

MC = os.path.join(os.environ.get('APPDATA', os.path.expanduser('~')), '.minecraft').replace(os.sep, '/')
V, L = MC + '/versions', MC + '/libraries'


def newest_neoforge():
    found = sorted((d.split('neoforge-')[-1] for d in os.listdir(V) if d.startswith('neoforge-21.1.')),
                   key=lambda v: [int(x) for x in v.split('.')])
    if not found:
        sys.exit('NeoForge 21.1.x is not installed in ' + V)
    return found[-1]


def libraries(version_id, out):
    data = json.load(open(f'{V}/{version_id}/{version_id}.json', encoding='utf-8'))
    if 'inheritsFrom' in data:
        libraries(data['inheritsFrom'], out)
    for lib in data.get('libraries', []):
        art = lib.get('downloads', {}).get('artifact')
        if art and art.get('path'):
            out.append(L + '/' + art['path'])
        elif 'name' in lib:
            g, n, v = lib['name'].split(':')[:3]
            out.append(f"{L}/{g.replace('.', '/')}/{n}/{v}/{n}-{v}.jar")


def main():
    out_file = sys.argv[1]
    nf = sys.argv[2] if len(sys.argv) > 2 else newest_neoforge()
    # patched classes first, then the full Mojang-named client, then the NeoForge API
    client = sorted(p.replace(os.sep, '/') for p in glob.glob(L + '/net/minecraft/client/1.21.1-*/client-1.21.1-*-srg.jar'))
    jars = [f'{L}/net/neoforged/neoforge/{nf}/neoforge-{nf}-client.jar', *client[-1:],
            f'{L}/net/neoforged/neoforge/{nf}/neoforge-{nf}-universal.jar']
    libraries(f'neoforge-{nf}', jars)
    jars = [j for j in dict.fromkeys(jars) if os.path.exists(j)]
    with open(out_file, 'w', encoding='utf-8') as f:
        f.write('-cp\n"' + ';'.join(jars) + '"\n')
    print(f'classpath: NeoForge {nf}, {len(jars)} jars')


main()
