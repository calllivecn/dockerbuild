#!/bin/bash
# date 2019-11-19 15:41:02
# author calllivecn <calllivecn@outlook.com>


CERTBOT_DIR="$HOME/.certbot"

certonly(){
	certbot certonly --config-dir $CERTBOT_DIR --work-dir $CERTBOT_DIR --logs-dir $CERTBOT_DIR \
		--preferred-challenges dns-01 \
		--manual --manual-auth-hook "script --change-txt" --manual-cleanup-hook "script --cleanup-txt" \
		--domain "calllive.cc" --domain "*.calllive.cc"
}

certrenew(){
	certbot renew --config-dir $CERTBOT_DIR --work-dir $CERTBOT_DIR --logs-dir $CERTBOT_DIR \
		--pre-hook "echo pre_hook \$RENEWED_LINEAGE \$RENEWED_DOMAINS >> /tmp/certbot.log" \
		--post-hook "echo post_hook RENEWED_LINEAGE=$RENEWED_LINEAGE RENEWED_DOMAINS=$RENEWED_DOMAINS >> /tmp/certbot.log" \
		#--manual-auth-hook "" 
		#--manual-cleanup-hook ""
		#--force-renewal
}

certrenew
