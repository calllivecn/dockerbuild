
echo "start shell pid is  $$"

safe_exit(){
echo "safe exit... pid $1"
kill $1
echo "成功安全退出"

}


CMD="java -Xms${MEM_MIN}M -Xmx${MEM_MAX}M -jar ${MC_SERVER} nogui"

echo '执行的命令 -->' $CMD

$CMD &

pid=$!

trap "safe_exit $pid" SIGTERM SIGINT

echo java进程 pid is $pid

sleep 3
ps -ef 

wait $pid

