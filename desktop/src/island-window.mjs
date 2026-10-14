import {mountNanfengIsland} from './nanfeng-island.mjs';
mountNanfengIsland({host:document.getElementById('island'),invoke:(command,args)=>window.__TAURI__.core.invoke(command,args),background:true});
