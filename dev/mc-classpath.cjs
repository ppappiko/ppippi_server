// node fallback for dev/mc-classpath.py (PCs without Python): node dev/mc-classpath.cjs <out.args>
const fs = require('fs'), path = require('path');
const MC = (process.env.APPDATA + '/.minecraft').split(path.sep).join('/'), V = MC + '/versions', L = MC + '/libraries';
const nf = fs.readdirSync(V).filter(d => d.startsWith('neoforge-21.1.')).map(d => d.split('neoforge-').pop())
  .sort((a, b) => { a = a.split('.').map(Number); b = b.split('.').map(Number); for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] - b[i]; return 0; }).pop();
const out = [];
function libs(id) {
  const d = JSON.parse(fs.readFileSync(`${V}/${id}/${id}.json`, 'utf8'));
  if (d.inheritsFrom) libs(d.inheritsFrom);
  for (const l of d.libraries || []) {
    const a = l.downloads && l.downloads.artifact;
    if (a && a.path) out.push(L + '/' + a.path);
    else if (l.name) { const [g, n, v] = l.name.split(':'); out.push(`${L}/${g.replace(/\./g, '/')}/${n}/${v}/${n}-${v}.jar`); }
  }
}
const cdir = L + '/net/minecraft/client';
const client = fs.readdirSync(cdir).filter(d => d.startsWith('1.21.1-'))
  .flatMap(d => fs.readdirSync(cdir + '/' + d).filter(f => /^client-1\.21\.1-.*-srg\.jar$/.test(f)).map(f => cdir + '/' + d + '/' + f)).sort();
const jars = [`${L}/net/neoforged/neoforge/${nf}/neoforge-${nf}-client.jar`, ...client.slice(-1), `${L}/net/neoforged/neoforge/${nf}/neoforge-${nf}-universal.jar`];
libs('neoforge-' + nf); jars.push(...out);
const ok = [...new Set(jars)].filter(j => fs.existsSync(j));
fs.writeFileSync(process.argv[2], '-cp\n"' + ok.join(';') + '"\n');
console.log('NeoForge', nf, ok.length, 'jars', client.slice(-1));
