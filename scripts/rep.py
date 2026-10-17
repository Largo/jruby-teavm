#!/usr/bin/env python3
# rep.py FILE  (reads pairs from stdin separated by lines '<<<<', '====', '>>>>')
# exact string replacement helper used while developing the patch; fails loudly if a pattern is missing/ambiguous
import sys
f=sys.argv[1]; s=open(f).read(); data=sys.stdin.read()
blocks=data.split('<<<<\n')[1:]
for b in blocks:
    old,new=b.split('====\n',1); new=new.split('>>>>\n',1)[0]
    n=s.count(old)
    if n!=1:
        print(f"{f}: pattern found {n} times:\n{old}",file=sys.stderr); sys.exit(1)
    s=s.replace(old,new)
open(f,'w').write(s)
