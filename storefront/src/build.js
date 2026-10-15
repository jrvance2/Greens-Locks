const {chromium}=require('playwright');const fs=require('fs'),path=require('path');
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium'});const p=await b.newPage();
for(const f of fs.readdirSync(__dirname).filter(x=>/^\d.*\.html$/.test(x))){
await p.goto('file://'+path.join(__dirname,f));await p.pdf({path:path.join(__dirname,'../product',f.replace('.html','.pdf')),format:'Letter',printBackground:true});}
await b.close()})()
