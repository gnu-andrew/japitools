///////////////////////////////////////////////////////////////////////////////
// Japize - Output a machine-readable description of a Java API.
// Copyright (C) 2004  Jeroen Frijters <jeroen@frijters.net>
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
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ClassFile implements ClassWrapper
{
    private static final int CONSTANT_Class = 7;
    private static final int CONSTANT_Fieldref = 9;
    private static final int CONSTANT_Methodref = 10;
    private static final int CONSTANT_InterfaceMethodref = 11;
    private static final int CONSTANT_String = 8;
    private static final int CONSTANT_Integer = 3;
    private static final int CONSTANT_Float = 4;
    private static final int CONSTANT_Long = 5;
    private static final int CONSTANT_Double = 6;
    private static final int CONSTANT_NameAndType = 12;
    private static final int CONSTANT_Utf8 = 1;

    private ConstantPoolItem[] constant_pool;
    private int access_flags;
    private int raw_access_flags;
    private String name;
    private String superClass;
    private String[] interfaces;
    private FieldInfoItem[] fields;
    private FieldInfoItem[] allFields;
    private MethodInfoItem[] methods;
    private MethodInfoItem[] allMethods;
    private boolean deprecated;

    private class ConstantPoolItem
    {
	Object getConstantValue()
	{
	    throw new InternalError();
	}
    }

    private class ClassConstantPoolItem extends ConstantPoolItem
    {
	int name_index;

	ClassConstantPoolItem(int name_index)
	{
	    this.name_index = name_index;
	}
    }

    private class FMIConstantPoolItem extends ConstantPoolItem
    {
	int class_index;
	int name_and_type_index;

	FMIConstantPoolItem(int class_index, int name_and_type_index)
	{
	    this.class_index = class_index;
	    this.name_and_type_index = name_and_type_index;
	}
    }

    private class StringConstantPoolItem extends ConstantPoolItem
    {
	int string_index;

	StringConstantPoolItem(int string_index)
	{
	    this.string_index = string_index;
	}

	Object getConstantValue()
	{
	    return getUtf8String(string_index);
	}
    }

    private class IntegerConstantPoolItem extends ConstantPoolItem
    {
	int _int;

	IntegerConstantPoolItem(int _int)
	{
	    this._int = _int;
	}

	Object getConstantValue()
	{
	    return new Integer(_int);
	}
    }

    private class FloatConstantPoolItem extends ConstantPoolItem
    {
	float _float;

	FloatConstantPoolItem(float _float)
	{
	    this._float = _float;
	}

	Object getConstantValue()
	{
	    return new Float(_float);
	}
    }

    private class LongConstantPoolItem extends ConstantPoolItem
    {
	long _long;

	LongConstantPoolItem(long _long)
	{
	    this._long = _long;
	}

	Object getConstantValue()
	{
	    return new Long(_long);
	}
    }

    private class DoubleConstantPoolItem extends ConstantPoolItem
    {
	double _double;

	DoubleConstantPoolItem(double _double)
	{
	    this._double = _double;
	}

	Object getConstantValue()
	{
	    return new Double(_double);
	}
    }

    private class NameAndTypeConstantPoolItem extends ConstantPoolItem
    {
	int name_index;
	int descriptor_index;

	NameAndTypeConstantPoolItem(int name_index, int descriptor_index)
	{
	    this.name_index = name_index;
	    this.descriptor_index = descriptor_index;
	}
    }

    private class Utf8ConstantPoolItem extends ConstantPoolItem
    {
	String string;

	Utf8ConstantPoolItem(String string)
	{
	    this.string = string;
	}
    }

    private abstract class FMInfoItem implements Wrapper
    {
	int access_flags;
	int name_index;
	int descriptor_index;
	boolean deprecated;

	FMInfoItem(DataInputStream in) throws IOException
	{
	    access_flags = in.readUnsignedShort();
	    name_index = in.readUnsignedShort();
	    descriptor_index = in.readUnsignedShort();
	}

	public int getModifiers()
	{
	    return access_flags;
	}

	public boolean isDeprecated()
	{
	    return deprecated;
	}
    }

    private class FieldInfoItem extends FMInfoItem implements FieldWrapper
    {
	private Object constantValue;

	FieldInfoItem(DataInputStream in) throws IOException
	{
	    super(in);
	    int attributes_count = in.readUnsignedShort();
	    for(int i = 0; i < attributes_count; i++)
	    {
		int attribute_name_index = in.readUnsignedShort();
		int attribute_length = in.readInt();
		String attributeName = getUtf8String(attribute_name_index);
		if(attributeName.equals("ConstantValue"))
		{
		    constantValue = constant_pool[in.readUnsignedShort()].getConstantValue();
		}
		else if(attributeName.equals("Deprecated"))
		{
		    deprecated = true;
		}
		else
		{
		    skip(in, attribute_length);
		}
	    }
	}

	public String getName()
	{
	    return getUtf8String(name_index);
	}

	public String getType()
	{
	    return getUtf8String(descriptor_index);
	}

	public boolean isPrimitiveConstant()
	{
	    return constantValue != null;
	}

	public Object getPrimitiveValue()
	{
	    return constantValue;
	}

	public ClassWrapper getDeclaringClass()
	{
	    return ClassFile.this;
	}

	public int compareTo(Object obj)
	{
	    return getName().compareTo(((FieldInfoItem)obj).getName());
	}
    }

    private class MethodInfoItem extends FMInfoItem implements CallWrapper
    {
	private String[] exceptions;

	MethodInfoItem(DataInputStream in) throws IOException
	{
	    super(in);
	    int attributes_count = in.readUnsignedShort();
	    for(int i = 0; i < attributes_count; i++)
	    {
		int attribute_name_index = in.readUnsignedShort();
		int attribute_length = in.readInt();
		String attributeName = getUtf8String(attribute_name_index);
		if(attributeName.equals("Exceptions"))
		{
		    int count = in.readUnsignedShort();
		    exceptions = new String[count];
		    for(int j = 0; j < count; j++)
		    {
			exceptions[j] = getClassConstantName(in.readUnsignedShort());
		    }
		}
		else if(attributeName.equals("Deprecated"))
		{
		    deprecated = true;
		}
		else
		{
		    skip(in, attribute_length);
		}
	    }
	    if(exceptions == null)
		exceptions = new String[0];
	}

	public String getName()
	{
	    String name = getUtf8String(name_index);
	    if(name.equals("<init>"))
		return "";
	    else
		return name;
	}

	public String getRawName()
	{
	    return getUtf8String(name_index);
	}

	public String getDescriptor()
	{
	    return getUtf8String(descriptor_index);
	}

	public String[] getParameterTypes()
	{
	    String sig = getUtf8String(descriptor_index);
	    ArrayList l = new ArrayList();
	    for(int i = 1; sig.charAt(i) != ')'; i++)
	    {
		int start = i;
		while(sig.charAt(i) == '[')
		    i++;
		if(sig.charAt(i) == 'L')
		    i = sig.indexOf(';', i);
		l.add(sig.substring(start, i + 1));
	    }
	    String[] p = new String[l.size()];
	    l.toArray(p);
	    return p;
	}

	public String[] getExceptionTypes()
	{
	    return exceptions;
	}

	public String getReturnType()
	{
	    if(getUtf8String(name_index).equals("<init>"))
		return "constructor";
	    String sig = getUtf8String(descriptor_index);
	    return sig.substring(sig.lastIndexOf(')') + 1);
	}

	public ClassWrapper getDeclaringClass()
	{
	    return ClassFile.this;
	}

	public boolean isDup()
	{
	    return false;
	}

	public int compareTo(Object obj)
	{
	    return getSig().compareTo(((MethodInfoItem)obj).getSig());
	}

	private String sig;

	String getSig() 
	{
	    if (sig == null) 
	    {
		sig = getName() + "(";
		String comma = "";
		String[] parameterTypes = getParameterTypes();
		for (int j = 0; j < parameterTypes.length; j++) 
		{
		    sig += comma + parameterTypes[j];
		    comma = ",";
		}
		sig += ")";
	    }
	    return sig;
	}

	boolean isInheritable()
	{
	    return !Modifier.isPrivate(access_flags) && !getUtf8String(name_index).equals("<init>");
	}
    }

    private class AttributeInfoItem
    {
	int attribute_name_index;
	int attribute_length;
	byte[] info;

	void read(DataInputStream in) throws IOException
	{
	    attribute_name_index = in.readUnsignedShort();
	    attribute_length = in.readInt();
	    info = new byte[attribute_length];
	    in.readFully(info);
	}
    }

    private ClassFile(InputStream inStream) throws IOException
    {
	DataInputStream in = new DataInputStream(inStream);
	if(in.readInt() != 0xCAFEBABE)
	{
	    throw new IOException("Illegal magic");
	}
	int minor_version = in.readUnsignedShort();
	int major_version = in.readUnsignedShort();
	int constant_pool_count = in.readUnsignedShort();
	constant_pool = new ConstantPoolItem[constant_pool_count];
	for(int i = 1; i < constant_pool_count; i++)
	{
	    switch(in.readUnsignedByte())
	    {
		case CONSTANT_Class:
		    constant_pool[i] = new ClassConstantPoolItem(in.readUnsignedShort());
		    break;
		case CONSTANT_Fieldref:
		case CONSTANT_Methodref:
		case CONSTANT_InterfaceMethodref:
		    constant_pool[i] = new FMIConstantPoolItem(in.readUnsignedShort(), in.readUnsignedShort());
		    break;
		case CONSTANT_String:
		    constant_pool[i] = new StringConstantPoolItem(in.readUnsignedShort());
		    break;
		case CONSTANT_Integer:
		    constant_pool[i] = new IntegerConstantPoolItem(in.readInt());
		    break;
		case CONSTANT_Float:
		    constant_pool[i] = new FloatConstantPoolItem(in.readFloat());
		    break;
		case CONSTANT_Long:
		    constant_pool[i] = new LongConstantPoolItem(in.readLong());
		    i++;
		    break;
		case CONSTANT_Double:
		    constant_pool[i] = new DoubleConstantPoolItem(in.readDouble());
		    i++;
		    break;
		case CONSTANT_NameAndType:
		    constant_pool[i] = new NameAndTypeConstantPoolItem(in.readUnsignedShort(), in.readUnsignedShort());
		    break;
		case CONSTANT_Utf8:
		    constant_pool[i] = new Utf8ConstantPoolItem(in.readUTF());
		    break;
		default:
		    throw new IOException("unrecognized constant pool item");
	    }
	}
	raw_access_flags = in.readUnsignedShort();
	access_flags = raw_access_flags | Modifier.STATIC;
	int this_class = in.readUnsignedShort();
	name = this_class == 0 ? null : getClassConstantName(this_class);
	int super_class = in.readUnsignedShort();
	superClass = (super_class == 0 || Modifier.isInterface(access_flags)) ? null : getClassConstantName(super_class);
	int interfaces_count = in.readUnsignedShort();
	interfaces = new String[interfaces_count];
	for(int i = 0; i < interfaces_count; i++)
	{
	    interfaces[i] = getClassConstantName(in.readUnsignedShort());
	}
	int fields_count = in.readUnsignedShort();
	fields = new FieldInfoItem[fields_count];
	for(int i = 0; i < fields_count; i++)
	{
	    fields[i] = new FieldInfoItem(in);
	}
	int methods_count = in.readUnsignedShort();
	methods = new MethodInfoItem[methods_count];
	for(int i = 0; i < methods_count; i++)
	{
	    methods[i] = new MethodInfoItem(in);
	}
	int attributes_count = in.readUnsignedShort();
	for(int i = 0; i < attributes_count; i++)
	{
	    int attribute_name_index = in.readUnsignedShort();
	    int attribute_length = in.readInt();
	    String attributeName = getUtf8String(attribute_name_index);
	    if(attributeName.equals("InnerClasses"))
	    {
		int count = in.readUnsignedShort();
		for(int j = 0; j < count; j++)
		{
		    int inner_class = in.readUnsignedShort();
		    int outer_class = in.readUnsignedShort();
		    int inner_name = in.readUnsignedShort();;
		    int access_flags = in.readUnsignedShort();
		    if(getClassConstantName(inner_class).equals(name))
		    {
                        int mask = Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PROTECTED | Modifier.STATIC;
			this.access_flags &= ~mask;
                        this.access_flags |= access_flags & mask;
		    }
		}
	    }
	    else if(attributeName.equals("Deprecated"))
	    {
		deprecated = true;
	    }
	    else
	    {
		skip(in, attribute_length);
	    }
	}
    }

    private String getClassConstantName(int idx)
    {
	return getUtf8String(((ClassConstantPoolItem)constant_pool[idx]).name_index).replace('/', '.');
    }

    private String getUtf8String(int idx)
    {
	return ((Utf8ConstantPoolItem)constant_pool[idx]).string;
    }

    public int getModifiers()
    {
	return access_flags;
    }

    public boolean isDeprecated()
    {
	return deprecated;
    }

    public String getName()
    {
	return name;
    }

    public ClassWrapper getSuperclass()
    {
	if(superClass == null)
	    return null;
	else
	    return forName(superClass);
    }
    
    private static boolean gotSerializable = false;
    private static ClassFile serializableClass = null;

    public boolean isSerializable()
    {
	if (!gotSerializable)
	{
	    gotSerializable = true;
	    try
	    {
		serializableClass = forName("java.io.Serializable");
	    }
	    catch (NoClassDefFoundError e) {}
	}
 	return serializableClass != null && !isInterface() && isSubTypeOf(serializableClass);
    }

    public boolean isSubTypeOf(ClassFile c)
    {
	if(this == c)
	    return true;

	for(int i = 0; i < interfaces.length; i++)
	{
	    if(forName(interfaces[i]).isSubTypeOf(c))
		return true;
	}

	if(superClass != null)
	    return forName(superClass).isSubTypeOf(c);
	else
	    return false;
    }

    // This code is partially based on GNU Classpath's implementation which is
    // (c) FSF and licensed under the GNU Library General Public License.
    public long getSerialVersionUID()
    {
	for(int i = 0; i < fields.length; i++)
	{
	    if(fields[i].getName().equals("serialVersionUID") &&
		fields[i].getType().equals("J") &&
		Modifier.isStatic(fields[i].getModifiers()) &&
		Modifier.isFinal(fields[i].getModifiers()))
	    {
                Long val = (Long)fields[i].getPrimitiveValue();
                if (val == null)
                {
                    // It's a blank final, we don't support that.
                    // (We'd have to run the <clinit> to get the value.)
                    System.err.println();
                    System.err.println("Warning: " + name + " has a blank final serialVersionUID");
                    return 0;
                }
		return val.longValue();
	    }
	}
	// The class didn't define serialVersionUID, so we have to compute it
	try 
	{
	    final MessageDigest md = MessageDigest.getInstance("SHA");
	    OutputStream digest = new OutputStream() 
	    {
		public void write(int b) 
		{
		    md.update((byte) b);
		}

		public void write(byte[] data, int offset, int length) 
		{
		    md.update(data, offset, length);
		}
	    };
	    DataOutputStream data_out = new DataOutputStream(digest);

	    data_out.writeUTF(getName());

	    int modifiers = raw_access_flags;
	    // just look at interesting bits
	    modifiers = modifiers & (Modifier.ABSTRACT  | Modifier.FINAL |
		Modifier.INTERFACE | Modifier.PUBLIC);
	    data_out.writeInt(modifiers);

            String[] interfaces = (String[])this.interfaces.clone();
	    Arrays.sort(interfaces);
	    for (int i = 0; i < interfaces.length; i++)
	    {
		data_out.writeUTF(interfaces[i]);
	    }

	    Arrays.sort(fields, new Comparator() 
	    {
		public int compare(Object o1, Object o2) 
		{
		    return compare((FieldWrapper)o1, (FieldWrapper)o2);
		}
		public int compare(FieldWrapper f1, FieldWrapper f2) 
		{
		    if (f1.getName().equals(f2.getName())) 
		    {
			return f1.getType().compareTo(f2.getType());
		    } 
		    else 
		    {
			return f1.getName().compareTo(f2.getName());
		    }
		}
	    });

	    for (int i = 0; i < fields.length; i++) 
	    {
		FieldInfoItem field = fields[i];
		modifiers = field.getModifiers();
		if (Modifier.isPrivate (modifiers) &&
		    (Modifier.isStatic(modifiers) ||
		    Modifier.isTransient(modifiers))) 
		{
		    continue;
		}

		data_out.writeUTF(field.getName());
		data_out.writeInt(modifiers);
		data_out.writeUTF(field.getType());
	    }

	    Arrays.sort(methods, new Comparator() 
	    {
		public int compare(Object o1, Object o2) 
		{
		    return compare((MethodInfoItem)o1, (MethodInfoItem)o2);
		}
		public int compare(MethodInfoItem m1, MethodInfoItem m2) 
		{
		    if (m1.getRawName().equals(m2.getRawName())) 
		    {
			return m1.getDescriptor().compareTo(m2.getDescriptor());
		    } 
		    else 
		    {
			return m1.getRawName().compareTo(m2.getRawName());
		    }
		}
	    });

	    for (int i = 0; i < methods.length; i++) 
	    {
		MethodInfoItem method = methods[i];
		modifiers = method.getModifiers();
		if (Modifier.isPrivate(modifiers)) 
		{
		    continue;
		}

		data_out.writeUTF(method.getRawName());
		data_out.writeInt(modifiers);
		// the replacement of '/' with '.' was needed to make computed
		// SUID's agree with those computed by JDK
		data_out.writeUTF(method.getDescriptor().replace('/', '.'));
	    }

	    data_out.close ();
	    byte[] sha = md.digest ();
	    long result = 0;
	    int len = sha.length < 8 ? sha.length : 8;
	    for (int i=0; i < len; i++)
		result += (long)(sha[i] & 0xFF) << (8 * i);
  
	    return result;
	}
	catch (NoSuchAlgorithmException e) 
	{
	    throw new RuntimeException("The SHA algorithm was not found to use in " +
		"computing the Serial Version UID for class " +
		getName());
	} 
	catch (IOException ioe) 
	{
	    throw new RuntimeException(ioe.getMessage());
	}
    }

    public CallWrapper[] getCalls()
    {
	if(allMethods == null)
	{
	    HashMap map = new HashMap();
	    ClassWrapper[] ifaces = getInterfaces();
	    for(int i = 0; i < ifaces.length; i++)
	    {
		MethodInfoItem[] m = (MethodInfoItem[])ifaces[i].getCalls();
		for(int j = 0; j < m.length; j++)
		{
		    if(!map.containsKey(m[j].getSig()))
			map.put(m[j].getSig(), m[j]);
		}
	    }
	    if(superClass != null)
	    {
		MethodInfoItem[] m = (MethodInfoItem[])getSuperclass().getCalls();
		for(int i = 0; i < m.length; i++)
		{
		    if(m[i].isInheritable())
			map.put(m[i].getSig(), m[i]);
		}
	    }
	    for(int i = 0; i < methods.length; i++)
	    {
		// JDK15: skip bridge methods (the ACC_VOLATILE bit corresponds to the ACC_BRIDGE bit)
		if(!Modifier.isVolatile(methods[i].getModifiers()))
		    map.put(methods[i].getSig(), methods[i]);
	    }
	    allMethods = new MethodInfoItem[map.size()];
	    map.values().toArray(allMethods);
	    Arrays.sort(allMethods);
	}
	return allMethods;
    }

    public boolean isInterface()
    {
	return Modifier.isInterface(access_flags);
    }

    public ClassWrapper[] getInterfaces()
    {
	ClassWrapper[] interfaceNames = new ClassWrapper[interfaces.length];
	for(int i = 0; i < interfaces.length; i++)
	{
	    interfaceNames[i] = forName(interfaces[i]);
	}
	return interfaceNames;
    }

    public FieldWrapper[] getFields()
    {
	if(allFields == null)
	{
	    HashMap map = new HashMap();
	    if(superClass != null)
	    {
		FieldInfoItem[] f = (FieldInfoItem[])getSuperclass().getFields();
		for(int i = 0; i < f.length; i++)
		{
		    map.put(f[i].getName(), f[i]);
		}
	    }
	    ClassWrapper[] ifaces = getInterfaces();
	    for(int i = 0; i < ifaces.length; i++)
	    {
		FieldInfoItem[] f = (FieldInfoItem[])ifaces[i].getFields();
		for(int j = 0; j < f.length; j++)
		{
		    map.put(f[j].getName(), f[j]);
		}
	    }
	    for(int i = 0; i < fields.length; i++)
	    {
		map.put(fields[i].getName(), fields[i]);
	    }
	    allFields = new FieldInfoItem[map.size()];
	    map.values().toArray(allFields);
	    Arrays.sort(allFields);
	}
	return allFields;
    }

    private static ClassPathEntry[] classpath;
    private static WeakHashMap cache = new WeakHashMap();

    private static abstract class ClassPathEntry
    {
	abstract ClassFile load(String name);
    }

    private static class JarClassPathEntry extends ClassPathEntry
    {
	private ZipFile zf;

	JarClassPathEntry(File f) throws IOException
	{
	    this.zf = new ZipFile(f);
	}

	ClassFile load(String name)
	{
	    try
	    {
		ZipEntry entry = zf.getEntry(name.replace('.', '/') + ".class");
		if(entry != null)
		{
		    InputStream in = zf.getInputStream(entry);
                    try
                    {
                        return new ClassFile(in);
                    }
                    finally
                    {
		        in.close();
                    }
		}
	    }
	    catch(IOException x)
	    {
	    }
	    return null;
	}
    }

    private static class DirClassPathEntry extends ClassPathEntry
    {
        private File dir;

        DirClassPathEntry(File dir)
        {
            this.dir = dir;
        }

        ClassFile load(String name)
        {
            try
            {
                File f = new File(dir, name.replace('.', File.separatorChar) + ".class");
                FileInputStream in = new FileInputStream(f);
                try
                {
                    return new ClassFile(in);
                }
                finally
                {
                    in.close();
                }
            }
            catch(IOException x)
            {
            }
            return null;
        }
    }

    public static ClassFile forName(String name)
    {
	ClassFile cf = (ClassFile)cache.get(name);
	if(cf != null)
	    return cf;
	for(int i = 0; i < classpath.length; i++)
	{
	    cf = classpath[i].load(name);
	    if(cf != null)
	    {
		cache.put(name, cf);
		return cf;
	    }
	}
	throw new NoClassDefFoundError(name);
    }

    public static void setClasspath(String classpath) throws IOException
    {
	ArrayList list = new ArrayList();
	StringTokenizer st = new StringTokenizer(classpath, File.pathSeparator);
	while(st.hasMoreTokens())
	{
	    File f = new File(st.nextToken());
	    if(f.isFile())
		list.add(new JarClassPathEntry(f));
	    else if(f.isDirectory())
                list.add(new DirClassPathEntry(f));
	}
	ClassFile.classpath = new ClassPathEntry[list.size()];
	list.toArray(ClassFile.classpath);
    }

    static void skip(DataInputStream dis, int count) throws IOException
    {
	while(count > 0)
	    count -= (int)dis.skip(count);
    }
}
