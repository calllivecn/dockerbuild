FROM alpine:edge

RUN apk add --no-cache openconnect

ADD entrypoint.sh /

HEALTHCHECK --interval=10s --timeout=10s --start-period=10s CMD ip link show tun0

CMD ["/entrypoint.sh"]

