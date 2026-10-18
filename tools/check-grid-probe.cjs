// Independent geometry-only assessment. Never changes GT or product code.
const fs=require('fs'),crypto=require('crypto'),path=require('path');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex'),json=p=>JSON.parse(fs.readFileSync(p,'utf8'));
const root='reports/m3-grid-refinement-v1/discrete-grid-probe-v1';
const cases=[['league-1610','league-centre-npc',1610],['league-1611','league-centre-npc',1611],['pc-1410','pc-connect',1410],['exploration-9870','exploration-npc',9870]];
const base='data/gt/frozen/ocr-all-scenes-v2-observation-r4',freeze=json(base+'/freeze.json');
const results=[];
for(const [name,id,n] of cases){const file=root+'/'+name+'.json',p=json(file),item=freeze.items.find(x=>x.id===id),layoutRef=item.payloads.find(x=>x.kind==='layout');
 const layoutPath=layoutRef.file?base+'/'+layoutRef.file:layoutRef.approvedSource;
 if(sha(layoutPath)!==layoutRef.sha256)throw Error('Frozen layout changed');const l=json(layoutPath);
 const verification=json(path.dirname(p.input)+'/verification.json'),frame=verification.frames.find(x=>x.frameIndex===n);
 if(!verification.allRgbAndPngReadbackHashesMatch||!frame||frame.errors.length)throw Error('Original evidence missing');
 const expected={originX:l.sourceOrigin[0],originY:l.sourceOrigin[1],scaleX:(l.sourceScaleXNumerator??l.sourceScaleNumerator)/(l.sourceScaleXDenominator??l.sourceScaleDenominator),scaleY:(l.sourceScaleYNumerator??l.sourceScaleNumerator)/(l.sourceScaleYDenominator??l.sourceScaleDenominator)};
 if(!Object.values(expected).every(Number.isFinite))throw Error('Unsupported layout scale schema');
 function assess(g){let mismatches=0,maxError=0;for(const [axis,count] of [['X',160],['Y',144]])for(let k=0;k<=count;k++)for(const f of [Math.floor,Math.ceil]){const a=f(g['origin'+axis]+k*g['scale'+axis]+(f===Math.floor?1e-7:-1e-7)),b=f(expected['origin'+axis]+k*expected['scale'+axis]+(f===Math.floor?1e-7:-1e-7));if(a!==b)mismatches++;maxError=Math.max(maxError,Math.abs(a-b));}return {mismatches,maxSourcePixelError:maxError};}
 const proposed={originX:p.discreteX.origin,originY:p.discreteY.origin,scaleX:p.discreteX.scale,scaleY:p.discreteY.scale};
 results.push({id,frame:n,input:{path:p.input,sha256:sha(p.input),rgbSha256:frame.rgbSha256,pts:frame.pts,timeBase:frame.timeBase},probe:{path:file,sha256:sha(file)},expected,current:assess(p.current.grid),discrete:assess(proposed),equalMinima:[p.discreteX.equalMinima,p.discreteY.equalMinima]});
}
const out=root+'/assessment-v2.json';if(fs.existsSync(out))throw Error('New assessment only');
const report={status:'discrete_edge_model_rejected_for_product_use',tool:{path:'tools/GridEdgeProbe.java',sha256:sha('tools/GridEdgeProbe.java')},freeze:{path:base+'/freeze.json',sha256:sha(base+'/freeze.json')},results,reason:'Pixel-period fits leave integer-lattice origin ambiguity; envelope anchor includes capture border. Ceil quantization also creates many equivalent minima even on an exact integer raster. No score-based snapping, no implementation change, no GT changes.',next:'Fit non-periodic structural anchors such as confirmed textbox corners, model source sampling explicitly, and preserve ties as uncertainty. Do not select a discrete minimum merely because it has zero edge cost.'};
fs.writeFileSync(out,JSON.stringify(report,null,2)+'\n');console.log(JSON.stringify(results.map(x=>({id:x.id,frame:x.frame,current:x.current,discrete:x.discrete,ties:x.equalMinima}))));
