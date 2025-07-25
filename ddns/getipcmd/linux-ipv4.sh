#!/bin/bash

ip -4 route get 1.1.1.1 | grep -oP "(?<=1.1.1.1 via )(\d{1,3}\.){1,3}\d{1,3} (?=dev )"
