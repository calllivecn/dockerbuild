#!/usr/bin/env python3
#coding=utf-8

from threading import Thread
from subprocess import Popen,PIPE
import signal,os


ENV={"MEM_MIN":512,"MEM_MAX":2014,"MC_SERVER":''}

for key in ENV.keys():
	tmp = os.getenv(key)
	if tmp is None:
		continue
	else:
		ENV[key]=tmp

CMD='java -Xms{MEM_MIN}M -Xmx{MEM_MAX}M '\
	'-jar {MC_SERVER} nogui'.format(**ENV)

sub_exit_cmd='stop'

p = Popen(CMD,stdin=PIPE,shell=True)#,universal_newlines=True)
ppid = os.getpid()

print('father pid',ppid)
print('sub pid ',p.pid)

def sig_handler(sig_number,SIG):
	cmd = sub_exit_cmd + os.linesep
	p.stdin.write(cmd.encode())
	p.stdin.flush()
	#exit(0)

signal.signal(signal.SIGTERM,sig_handler)

def cmd_input():
	cmd = ''
	while cmd != sub_exit_cmd:
		cmd = input()
		cmd2 = cmd + os.linesep
		p.stdin.write(cmd2.encode())
		p.stdin.flush()
	else:
		cmd = sub_exit_cmd + os.linesep
		p.stdin.write(cmd.encode())
		p.stdin.flush()

th = Thread(target=cmd_input,daemon=True)
th.start()

print('wait sub end...')

try:
	print('subprocess returncode:',p.wait())
except KeyboardInterrupt:
	sig_handler(1,2)

print('safe exit')

