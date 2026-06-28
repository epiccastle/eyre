ARG base_image
FROM ${base_image}
ARG root_password
RUN if command -v apk > /dev/null; then \
      apk update && apk add openssh-server openssh file curl netcat-openbsd; \
    elif command -v apt-get > /dev/null; then \
      apt-get update && apt-get install -y openssh-server file curl netcat-openbsd; \
    elif command -v dnf > /dev/null; then \
      dnf install -y --allowerasing openssh-server file curl nmap-ncat; \
    elif command -v pacman > /dev/null; then \
      pacman -Syu --noconfirm openssh file curl openbsd-netcat; \
    fi
RUN sed -E "s/^#*(PermitRootLogin ).+$/\1yes/" /etc/ssh/sshd_config -i
RUN sed -E "s/^#*(AllowTcpForwarding ).+$/\1yes/" /etc/ssh/sshd_config -i
RUN ssh-keygen -A
ENTRYPOINT ["/usr/sbin/sshd","-D","-E","/tmp/sshd.log"]
EXPOSE 22/tcp
RUN echo "root:$root_password"|chpasswd
