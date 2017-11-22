#!/bin/sh

PLAT_RUNNER=@@runscript@@
SERV_NAME=@@servname@@

export KITE_BASE_DIR=@@kitebase@@
export JAVA_CONFIG="@@javaconfig@@"

case "$1" in
    start)

        $PLAT_RUNNER $SERV_NAME start

        ;;

    stop)
        $PLAT_RUNNER $SERV_NAME stop

        ;;

    restart)
        $0 stop
        $0 start
        ;;

    force-reload)
        $0 restart
        ;;

    status)
        ;;

    *)
        echo "Usage: $0 {start|stop|restart|status}"
        exit 1
        ;;
esac

exit 0
