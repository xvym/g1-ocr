'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto'),cp=require('child_process');
const out=process.argv[2];if(!out||fs.existsSync(out))throw Error('Use a new implementation evidence directory');
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const walk=p=>fs.readdirSync(p,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(p,e.name)):[path.join(p,e.name)]);
fs.mkdirSync(out,{recursive:true});
const files=cp.execFileSync('rg',['--files','app/src','verification/src','tools','config','resources'],{encoding:'utf8'}).trim().split(/\r?\n/).concat(['pom.xml','app/pom.xml','verification/pom.xml','.gitattributes']).sort();
const identity=p=>({path:p.replace(/\\/g,'/'),sha256:hash(p),bytes:fs.statSync(p).size});
fs.writeFileSync(path.join(out,'source-snapshot.json'),JSON.stringify({gitHead:cp.execFileSync('git',['rev-parse','HEAD'],{encoding:'utf8'}).trim(),files:files.map(identity)},null,2)+'\n');
for(const p of files.filter(p=>p.endsWith('.java')&&(p.includes('ocr')||p.includes('OcrReplay')||p.includes('GlyphReadAudit')))){
 const target=path.join(out,'source',p);fs.mkdirSync(path.dirname(target),{recursive:true});fs.copyFileSync(p,target);if(hash(p)!==hash(target))throw Error('Source copy differs');
}
const classes=['app/target/classes','verification/target/classes'].flatMap(walk).filter(p=>p.endsWith('.class')).sort();
fs.writeFileSync(path.join(out,'class-identity.json'),JSON.stringify({sourceSnapshotSha256:hash(path.join(out,'source-snapshot.json')),classes:classes.map(identity),meaning:'Compiled class identity only. Package and video accuracy are separate checks.'},null,2)+'\n');
console.log('Implementation sources and compiled class identities saved: '+files.length+' sources, '+classes.length+' classes.');
