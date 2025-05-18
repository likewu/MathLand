#! /bin/bash

SCRIPT_PATH=$(realpath ${BASH_SOURCE})
sudo rm -f $SCRIPT_PATH

if [ ! -f "$HOME"/.local/bin/nu ]; then
   #sudo apt-get update
   cpuarch=$(uname -m)
   cpuarch=${cpuarch:-"aarch64"}
   wget https://gitlab.com/api/v4/projects/30140969/packages/generic/UserLAnd-Assets-Debian/3/nu-0.74.0-${cpuarch}-unknown-linux-gnu.tar.gz
   tar -xzf nu-0.74.0-${cpuarch}-unknown-linux-gnu.tar.gz
   #sudo DEBIAN_FRONTEND=noninteractive apt-get -y install r-base r-base-dev 
   if [[ $? != 0 ]]; then
      read -rsp $'An error occurred installing packages, please try again and if it persists provide this log to the developer.\nPress any key to close...\n' -n1 key
   fi
   rm nu-0.74.0-${cpuarch}-unknown-linux-gnu.tar.gz
   cd
   mkdir -p .local/bin
   #echo "export PATH=\$HOME/.local/bin:\$PATH" >> ~/.bashrc
   #. ~/.bashrc
   #ln -s "$HOME"/nu-0.74.0-${cpuarch}-unknown-linux-gnu/nu "$HOME"/.local/bin
   echo LD_LIBRARY_PATH="$HOME"/nu-0.74.0-${cpuarch}-unknown-linux-gnu/ "$HOME"/nu-0.74.0-${cpuarch}-unknown-linux-gnu/nu $@ > "$HOME"/.local/bin/nu
   chmod +x "$HOME"/.local/bin/nu

   /home/leafcolor/nu-0.74.0-aarch64-unknown-linux-gnu/patchelf --set-interpreter /lib/ld-linux-aarch64.so.2 /home/leafcolor/nu-0.74.0-aarch64-unknown-linux-gnu/nu

   rm /lib/ld-linux-aarch64.so.2
   ln -s /home/leafcolor/nu-0.74.0-aarch64-unknown-linux-gnu/ld-2.34.9000.so /lib/ld-linux-aarch64.so.2

   cd /home/leafcolor/nu-0.74.0-aarch64-unknown-linux-gnu
   rm libc.so.6
   rm libm.so.6
   rm libpthread.so.0
   rm libdl.so.2
   ln -s libc-2.34.9000.so libc.so.6
   ln -s libm-2.34.9000.so libm.so.6
   ln -s libpthread-2.34.9000.so libpthread.so.0
   ln -s libdl-2.34.9000.so libdl.so.2

   cp -f /home/leafcolor/nu-0.74.0-aarch64-unknown-linux-gnu/config.nu $HOME/.config/nushell/config.nu
   cp -f /home/leafcolor/nu-0.74.0-aarch64-unknown-linux-gnu/env.nu $HOME/.config/nushell/env.nu

   #echo let-env LD_LIBRARY_PATH = "/lib/aarch64-linux-gnu/" >> .config/nushell/env.nu

   #mkdir $HOME/.termux;
   #echo "extra-keys = [['ESC','/','-','$','HOME','UP','END','PGUP'],['TAB','CTRL','ALT','?','LEFT','DOWN','RIGHT','PGDN']]" > $HOME/.termux/termux.properties
fi
"$HOME"/.local/bin/nu
exit


#readelf -d nu
#patchelf --print-interpreter nu
#patchelf --set-interpreter /lib/ld-linux-aarch64.so.2 nu
