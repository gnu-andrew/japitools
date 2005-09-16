///////////////////////////////////////////////////////////////////////////////
// Japize - Output a machine-readable description of a Java API.
// Copyright (C) 2000,2002,2003,2004  Stuart Ballard <stuart.a.ballard@gmail.com>
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
///////////////////////////////////////////////////////////////////////////////

package net.wuffies.japi;
import java.lang.reflect.Modifier;

// Note that a TypeParam *must* correspond to one of the entries in its
// getAssociatedWrapper().getTypeParams() array.
public class TypeParam extends RefType {
  private GenericWrapper associatedWrapper;
  private String name;
  private ClassType primaryConstraint;
  public TypeParam(GenericWrapper associatedWrapper, String name, RefType primaryConstraint) {
    this.associatedWrapper = associatedWrapper;
    this.name = name;
    if (primaryConstraint instanceof ClassType)
        this.primaryConstraint = (ClassType)primaryConstraint;
    else
        this.primaryConstraint = new ClassType("java.lang.Object");
  }
  // Later we'll support other constraints once we understand better what's permitted
  // and what's not.

  public GenericWrapper getAssociatedWrapper() {
    return associatedWrapper;
  }
  public String getName() {
    return name;
  }
  public ClassType getPrimaryConstraint() {
    return primaryConstraint;
  }
  public int getIndex(GenericWrapper wrapper) {
    TypeParam[] params = getAllTypeParams(wrapper);
    for (int i = 0; i < params.length; i++) {
      if (params[i] == this) return i;
    }
    return -1;
  }
  public String getTypeSig(GenericWrapper wrapper) {
    int index = getIndex(wrapper);
    if (index < 0) throw new RuntimeException("Unbound type parameter " + this + " not associated with current wrapper " + wrapper);
    return "@" + index;
  }
  public String getNonGenericTypeSig() {
    return getPrimaryConstraint().getNonGenericTypeSig();
  }
  public void resolveTypeParameters() {
    primaryConstraint.resolveTypeParameters();
  }

  public Type bind(ClassType t) {
    debugStart("Bind", "to " + t);
    try {
      int index = getIndex(t.getWrapper());
      if (index == -1) {
        return new TypeParam(getAssociatedWrapper(), getName(), (RefType) primaryConstraint.bind(t));
      } else if (t.getTypeArguments() == null) {
        return null;
      } else {
        return t.getTypeArguments()[index];
      }
    } finally {
      debugEnd();
    }
  }

  public Type bindWithFallback(ClassType t) {
    Type result = bind(t);
    return result != null ? result : getPrimaryConstraint().bindWithFallback(t);
  }

  public String toStringImpl() {
    return "TypeParam:" + associatedWrapper.toString() + "-" + name + "/" + primaryConstraint.toString();
  }

  /**
   * Find all the type params of a particular GenericWrapper, including those inherited
   * from its containers.
   */
  public static TypeParam[] getAllTypeParams(GenericWrapper wrapper) {
    int count = 0;
    GenericWrapper container = wrapper;
    while (container != null) {
      if (container.getTypeParams() != null) {
        count += container.getTypeParams().length;
      }
      container = getContainingWrapper(container);
    }

    if (count == 0) return null;

    TypeParam[] params = new TypeParam[count];
    container = wrapper;
    int start = params.length;
    while (container != null) {
      TypeParam[] cparams = container.getTypeParams();
      if (cparams != null) {
        start -= cparams.length;
        for (int i = 0; i < cparams.length; i++) {
          params[start + i] = cparams[i];
        }
      }
      container = getContainingWrapper(container);
    }
    if (start != 0) throw new RuntimeException("Oops, internal error, didn't completely fill typeparams array");
    return params;
  }

  // Static methods and static inner classes do not inherit their container's type params, but
  // nonstatic things do.
  private static GenericWrapper getContainingWrapper(GenericWrapper wrapper) {
    if ((wrapper.getModifiers() & Modifier.STATIC) != 0) {
      return null;
    } else {
      return wrapper.getContainingWrapper();
    }
  }
}
