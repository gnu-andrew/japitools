///////////////////////////////////////////////////////////////////////////////
// Japize - Output a machine-readable description of a Java API.
// Copyright (C) 2000  Stuart Ballard <sballard@wuffies.net>
// 
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
// 
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
// 
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// A link to the GNU General Public License is available at the
// japitools homepage, http://stuart.wuffies.net/japi/
///////////////////////////////////////////////////////////////////////////////

package net.wuffies.japi;
import jode.bytecode.*;
import java.lang.reflect.Modifier;

class JodeField implements FieldWrapper {
  private FieldInfo f;
  private JodeClass jc;
  JodeField(FieldInfo f, JodeClass jc) {
    this.f = f;
    this.jc = jc;
  }
  public int getModifiers() {
    return f.getModifiers();
  }
  public String getName() {
    return f.getName();
  }
  public String getType() {
    return JodeClass.typeFor(f.getType());
  }
  public boolean isPrimitiveConstant() {
    int mods = f.getModifiers();
    if (Modifier.isStatic(mods) && Modifier.isFinal(mods) &&
        (Modifier.isPublic(mods) || Modifier.isProtected(mods)) &&
        f.getConstant() != null) {
      return true;
    } else {
      return false;
    }
  }
  public Object getPrimitiveValue() {
    return f.getConstant();
  }
  public ClassWrapper getDeclaringClass() {
    return jc;
  }
  public boolean equals(Object o) {
    return o instanceof JodeField && ((JodeField)o).getName().equals(getName());
  }
}

