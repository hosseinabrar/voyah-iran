export const puzzleLayers={"hood_close":{"x":-105.11576,"y":-0.37341,"w":210.23152,"h":147.49813},"rear_open":{"x":-106.236,"y":401.04556,"w":212.84541,"h":99.32786},"left_close":{"x":-112.95743,"y":147.12472,"w":112.77072,"h":253.92084},"left_fl":{"x":-190.25392,"y":147.12472,"w":190.06721,"h":253.92084},"left_rl":{"x":-184.27931,"y":147.12472,"w":184.09261,"h":253.92084},"left_both":{"x":-190.25392,"y":147.12472,"w":190.06721,"h":253.92084},"right_close":{"x":-0.18671,"y":147.12472,"w":113.14414,"h":253.92084},"right_fr":{"x":-0.18671,"y":147.12472,"w":190.44063,"h":253.92084},"right_rr":{"x":-0.18671,"y":147.12472,"w":184.46602,"h":253.92084},"right_both":{"x":-0.18671,"y":147.12472,"w":189.6938,"h":253.92084},"hood_open":{"x":-105.48917,"y":-0.74683,"w":210.60493,"h":147.87155},"rear_close":{"x":-106.236,"y":401.04556,"w":212.84541,"h":88.49888}};
export function selectCarPieces(v={}) {
 const side=(front,rear,a,b)=>front===true?(rear===true?'both':a):(rear===true?b:'close');
 return ['hood_'+(v.hood===true?'open':'close'),
 'left_'+side(v.doorFL,v.doorRL,'fl','rl'),
 'right_'+side(v.doorFR,v.doorRR,'fr','rr'),
 'rear_'+(v.trunk===true?'open':'close')];
}
