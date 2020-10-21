#!/bin/ash
# date 2020-10-21 15:09:13
# author calllivecn <c-all@qq.com>


echo $PW | openconnect  --user $USER --password-on-stdin --timestamp $SERVER
